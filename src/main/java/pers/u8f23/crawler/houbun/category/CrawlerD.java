package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.config.EmailConfig;
import pers.u8f23.crawler.houbun.category.config.RootConfig;
import pers.u8f23.crawler.houbun.category.response.ApiBaseResponse;
import pers.u8f23.crawler.houbun.category.response.Query;
import retrofit2.Response;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 8f23
 * @create 2023/8/4-20:23
 */
@Slf4j
public final class CrawlerD
{
	private static final int RETRY_TIMES = 10;
	private static final int BACKUP_BUFFER_SIZE = 1024 * 4;
	private final Map<String, Set<String>> resultSet = new HashMap<>();
	private final Set<String> visitedSet = Collections.synchronizedSet(new HashSet<>());
	private final AtomicInteger requestingCounter = new AtomicInteger(0);
	@NonNull
	private final RootConfig config;
	private volatile boolean mainPagesStarted = false;
	private List<String> simplifiedList;


	public CrawlerD(
		@NonNull RootConfig config
	)
	{
		this.config = config;
		requestRootCate(config.getRootCateTitle(), config.getRootCateTitle());
		Completable.fromAction(this::afterMainPages)
			.delay(1, TimeUnit.SECONDS)
			.repeatUntil(() -> mainPagesStarted)
			.subscribeOn(Schedulers.newThread())
			.subscribe();
	}

	private void requestRootCate(String root, String path)
	{
		if (path == null || path.startsWith("User:"))
		{
			return;
		}
		if (visitedSet.contains(path))
		{
			return;
		}
		if (path.startsWith("Category:") || path.startsWith("index.php?title=Category:"))
		{
			requestingCounter.incrementAndGet();
			// root cate or sub cate.
			Single<Response<ResponseBody>> single =
				path.startsWith("Category:")
					? HomeSiteService.getInstance().get(path)
					: HomeSiteService.getInstance().getUrl(path);
			single.subscribeOn(provideRequestScheduler())
				.observeOn(Schedulers.computation())
				.map(HttpUtils::parseRawHtmlCategoryPage)
				.doOnSuccess(set -> set.forEach(i -> requestRootCate(root, i)))
				.doFinally(() -> visitedSet.add(path))
				.doFinally(requestingCounter::decrementAndGet)
				.retry(RETRY_TIMES)
				.subscribe();
		}
		else
		{
			// work pages and some other pages.
			// add to the result set.
			submitItem(root, path);
			// request work.
			queryCreators(path);
			this.requestWorkCate(path, "Category:" + path);
		}
	}

	private void requestWorkCate(String root, String path)
	{
		if (path == null || path.startsWith("User:"))
		{
			return;
		}
		if (visitedSet.contains(path))
		{
			return;
		}
		if (path.startsWith("Category:") || path.startsWith("index.php?title=Category:"))
		{
			requestingCounter.incrementAndGet();
			// work cate or sub cate.
			Single<Response<ResponseBody>> single =
				path.startsWith("Category:")
					? HomeSiteService.getInstance().get(path)
					: HomeSiteService.getInstance().getUrl(path);
			single
				.subscribeOn(provideRequestScheduler())
				.observeOn(Schedulers.computation())
				.map(HttpUtils::parseRawHtmlCategoryPage)
				.doOnSuccess(set -> set.forEach(i -> requestWorkCate(root, i)))
				.doAfterTerminate(() -> visitedSet.add(path))
				.doAfterTerminate(requestingCounter::decrementAndGet)
				.retry(RETRY_TIMES)
				.subscribe();
		}
		else
		{
			// work sub-page.
			submitItem(root, path);
		}
	}

	private void submitItem(String key, String value)
	{
		if (value.startsWith("User:")
		    || value.startsWith("index.php?title=Category:"))
		{
			// ignore
			return;
		}
		log.info("log data:[{}, {}]", key, value);
		Completable
			.fromRunnable(() -> {
				Set<String> set = resultSet.computeIfAbsent(key, k -> new HashSet<>());
				set.add(value);
			})
			.subscribeOn(Schedulers.single())
			.subscribe();
	}

	private void afterMainPages() throws Exception
	{
		if (requestingCounter.get() > 0 || mainPagesStarted)
		{
			log.info("afterMainPages idle");
			return;
		}
		mainPagesStarted = true;
		log.info("afterMainPages run");
		Gson gson = new Gson();
		try (FileOutputStream fos = new FileOutputStream(this.config.getOutputFilePath()))
		{
			fos.write(gson.toJson(this.resultSet).getBytes());
		}
		Set<String> simplifiedSet = new HashSet<>();
		this.resultSet.forEach((k, v) -> {
			simplifiedSet.add(k);
			simplifiedSet.addAll(v);
		});
		List<String> simplifiedList = new ArrayList<>(simplifiedSet);
		this.simplifiedList = simplifiedList;
		Collections.sort(simplifiedList);
		StringBuilder titleInLinesBuilder = new StringBuilder();
		for (String line : simplifiedList)
		{
			titleInLinesBuilder.append(line);
			titleInLinesBuilder.append("\n");
		}
		String titleInLines = titleInLinesBuilder.toString();
		try (PrintStream ps = new PrintStream(config.getOutputSimplifiedFilePath()))
		{
			ps.print(titleInLines);
		}
		// request backupFile
		queryBackup(titleInLines);
	}

	private void queryCreators(String workName)
	{
		if (workName.startsWith("User:")
		    || workName.startsWith("index.php?title=Category:"))
		{
			// ignore
			return;
		}
		requestingCounter.incrementAndGet();
		HomeSiteService.getInstance()
			.getCategories(workName)
			.subscribeOn(provideRequestScheduler())
			.observeOn(Schedulers.computation())
			.map(HttpUtils::parseResponse)
			.map(ApiBaseResponse::getQuery)
			.map(Query::flat)
			.doOnSuccess(map -> map.forEach((k, vs) -> vs.forEach(v ->
				submitItem(k, v)
			)))
			.doAfterTerminate(requestingCounter::decrementAndGet)
			.retry(RETRY_TIMES)
			.subscribe();
	}

	private void queryBackup(String titleInLines)
	{
		log.info("queryBackup start");
		requestingCounter.incrementAndGet();

		MirrorSiteService.getInstance()
			.requestMirror(titleInLines, "1", "1", "+\\", "Special:导出页面")
			.subscribeOn(provideRequestScheduler())
			.observeOn(Schedulers.newThread())
			.retry(RETRY_TIMES)
			.doOnSuccess(res -> {
				ResponseBody body = res.body();
				if (body == null)
				{
					throw new NullPointerException();
				}
				try (InputStream is = body.byteStream())
				{
					try (OutputStream fos = Files.newOutputStream(
						Paths.get(config.getBackupFilePath())))
					{
						byte[] buffer = new byte[BACKUP_BUFFER_SIZE];
						while (true)
						{
							int i = is.read(buffer);
							if (i < 0)
							{
								break;
							}
							fos.write(buffer, 0, i);
						}
					}
				}
				log.info("finish backup.");
				sendReportEmail(true, null);
			})
			.doOnError((th) -> {
				log.info("Failed to query backup.", th);
				sendReportEmail(false, th);
			})
			.doFinally(requestingCounter::decrementAndGet)
			.doFinally(() -> log.info("queryBackup final:{}", titleInLines))
			.subscribe();
	}

	private Scheduler provideRequestScheduler()
	{
		return Schedulers.single();
	}

	private void sendReportEmail(boolean success, Throwable reason)
	{
		requestingCounter.incrementAndGet();
		log.info("start to send mail for {}.", success);
		EmailConfig config = this.config.getMailConfig();
		String emailHost = config.getEmailHost();
		String transportType = config.getTransportType();
		String fromUser = config.getFromUser();
		String fromEmail = config.getFromEmail();
		String authCode = config.getAuthCode();
		String toEmail = "";
		List<String> copyToEmail = Collections.emptyList();
		String subject = "";
		try
		{
			Properties props = new Properties();
			props.setProperty("mail.transport.protocol", transportType);
			props.setProperty("mail.host", emailHost);
			props.setProperty("mail.user", fromUser);
			props.setProperty("mail.from", fromEmail);
			Session session = Session.getInstance(props, null);
			// session.setDebug(true);
			MimeMessage message = new MimeMessage(session);
			InternetAddress from = new InternetAddress(fromEmail);
			message.setFrom(from);
			InternetAddress to = new InternetAddress(toEmail);
			message.setRecipient(Message.RecipientType.TO, to);
			List<InternetAddress> copyAddresses = new ArrayList<>();
			for (String addr : copyToEmail)
			{
				copyAddresses.add(new InternetAddress(addr));
			}
			InternetAddress[] addressesArr = copyAddresses.toArray(new InternetAddress[0]);
			message.setRecipients(Message.RecipientType.CC, addressesArr);
			message.setSubject((success ? "[SUCCESS] " : "[FAILURE] ") + subject);
			StringBuilder mailContentBuilder = new StringBuilder();
			mailContentBuilder
				.append("<h1>").append(subject).append("</h1>")
				.append(success ? "<h3>Success!</h3>" : "<h3>Failure!</h3>")
				.append("<div>Visited ").append(visitedSet.size()).append(" page(s).</div>")
				.append("<div>Recorded ").append(simplifiedList.size()).append(" page(s).</div>")
				.append("<div>Backup file length is [")
				.append(getBackupFileSize()).append("] bytes.</div>")
				.append("<hr/>")
				.append("<div>list:</div><ol>");
			if (!success && reason != null)
			{
				ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream(1024 * 16);
				reason.printStackTrace(new PrintStream(arrayOutputStream));
				String reasonString = arrayOutputStream.toString();
				mailContentBuilder.append("<hr/><div>Reason:</div>")
					.append("<div>");
				for (String line : reasonString.split("\n"))
				{
					mailContentBuilder.append("<div>").append(line).append("</div>");
				}
				mailContentBuilder.append("</div>");
			}
			for (String item : simplifiedList)
			{
				mailContentBuilder.append("<li>").append(item).append("</li>");
			}
			mailContentBuilder.append("</ol>");
			String mailContent = mailContentBuilder.toString();
			message.setContent(mailContent, "text/html;charset=UTF-8");
			message.saveChanges();
			Transport transport = session.getTransport();
			transport.connect(emailHost, fromEmail, authCode);
			transport.sendMessage(message, message.getAllRecipients());
			log.info("end to send mail.");
			System.exit(0);
		}
		catch (Exception e)
		{
			log.error("Failed to send report mail!");
			System.exit(-1);
		}
		requestingCounter.decrementAndGet();
	}

	private long getBackupFileSize()
	{
		File f = new File(config.getBackupFilePath());
		return f.length();
	}
}
