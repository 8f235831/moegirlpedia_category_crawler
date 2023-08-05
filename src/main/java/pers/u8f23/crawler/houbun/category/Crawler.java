package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.config.EmailConfig;
import pers.u8f23.crawler.houbun.category.config.RootConfig;
import pers.u8f23.crawler.houbun.category.response.ApiBaseResponse;
import pers.u8f23.crawler.houbun.category.response.Query;
import retrofit2.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
public final class Crawler
{
	private static final int RETRY_TIMES = 10;
	private final Map<String, Set<String>> resultSet = new HashMap<>();
	private final Set<String> visitedSet = Collections.synchronizedSet(new HashSet<>());
	private final AtomicInteger requestingCounter = new AtomicInteger(0);
	@NonNull
	private final RootConfig config;
	@Getter
	private volatile boolean mainPagesFinished = false;
	private List<String> simplifiedList;


	public Crawler(
		@NonNull RootConfig config
	)
	{
		this.config = config;
		requestRootCate(config.getRootCateTitle(), config.getRootCateTitle());
		Completable.fromAction(this::afterMainPages)
			.delay(1, TimeUnit.SECONDS)
			.repeatUntil(() -> mainPagesFinished || requestingCounter.get() <= 0)
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
				.doAfterTerminate(() -> visitedSet.add(path))
				.doAfterTerminate(requestingCounter::decrementAndGet)
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
		if (requestingCounter.get() > 0)
		{
			return;
		}
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
		mainPagesFinished = true;
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
		requestingCounter.incrementAndGet();

		MirrorSiteService.getInstance()
			.requestMirror(titleInLines, "1", "1", "+\\", "Special:导出页面")
			.subscribeOn(provideRequestScheduler())
			.observeOn(Schedulers.computation())
			.doAfterSuccess(res -> {
				ResponseBody body = res.body();
				if (body != null)
				{
					body.close();
				}
			})
			.map(res -> Objects.requireNonNull(res.body()).byteString()
				.string(StandardCharsets.UTF_8))
			.observeOn(Schedulers.io())
			.doOnSuccess(string -> {
				try (
					OutputStream fos =
						Files.newOutputStream(Paths.get(config.getBackupFilePath())))
				{
					fos.write(string.getBytes(StandardCharsets.UTF_8));
				}
			})
			.doAfterTerminate(requestingCounter::decrementAndGet)
			.retry(RETRY_TIMES)
			.doAfterSuccess((o) -> sendReportEmail(true))
			.doOnError((th) -> sendReportEmail(false))
			.subscribe();
	}

	private Scheduler provideRequestScheduler()
	{
		return Schedulers.single();
	}

	private void sendReportEmail(boolean success)
	{
		EmailConfig config = this.config.getMailConfig();
		String emailHost = config.getEmailHost();
		String transportType = config.getTransportType();
		String fromUser = config.getFromUser();
		String fromEmail = config.getFromEmail();
		String authCode = config.getAuthCode();
		String toEmail = config.getToEmail();
		List<String> copyToEmail = config.getCopyToEmail();   //收件人邮箱
		String subject = config.getSubject();           //主题信息
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
			message.setSubject(subject);
			StringBuilder mailContentBuilder = new StringBuilder();
			mailContentBuilder
				.append("<h1>").append(subject).append("</h1>")
				.append(success ? "<h3>Success!</h3>" : "<h3>Failed!</h3>")
				.append("<div>Visited ").append(visitedSet.size()).append(" page(s).</div>")
				.append("<div>Record ").append(simplifiedList.size()).append(" page(s).</div>")
				.append("<div>Backup file length [")
				.append(getBackupFileSize()).append("] bytes.</div>")
				.append("<hr/>")
				.append("<div>list:</div><ol>");
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
		}
		catch (Exception e)
		{
			log.error("Failed to send report mail!");
		}
	}

	private long getBackupFileSize()
	{
		File f = new File(config.getBackupFilePath());
		return f.length();
	}
}
