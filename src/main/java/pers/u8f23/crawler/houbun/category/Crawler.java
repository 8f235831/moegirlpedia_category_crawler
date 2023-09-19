package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import pers.u8f23.crawler.houbun.category.config.EmailConfig;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author 8f23
 * @create 2023/9/5-12:36
 */
@Slf4j
public final class Crawler implements Runnable
{
	private static final int DEFAULT_RETRY_TIMES = 10;
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	private final Single<? extends Set<String>> backupListCollector;
	private final String outputPath;
	private final String compressedFilePath;
	private final String exceptionTraceFilePath;
	private final EmailConfig emailConfig;
	private final int retryTimes;
	private final int bufferSize;
	private Set<String> pages = Collections.emptySet();

	private Crawler(
		final Single<? extends Set<String>> backupListCollector,
		final String outputPath,
		final String compressedFilePath,
		final String exceptionTraceFilePath, final EmailConfig emailConfig,
		final int retryTimes,
		final int bufferSize
	)
	{
		this.backupListCollector = backupListCollector;
		this.outputPath = outputPath;
		this.compressedFilePath = compressedFilePath;
		this.exceptionTraceFilePath = exceptionTraceFilePath;
		this.emailConfig = emailConfig;
		this.retryTimes = retryTimes;
		this.bufferSize = bufferSize;
	}

	@Override
	public void run()
	{
		try
		{
			this.backupListCollector
				.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.computation())
				.doOnSuccess(pages ->
					this.pages = Objects.requireNonNull(pages)
				)
				.map(this::concatPagesTitles)
				.doOnSuccess(any -> log.info(
					"Collected pages success to map, then turn to downloading backup file."
				))
				.flatMapCompletable(pages ->
					Completable.fromAction(this::downloadBackupFile)
				)
				.andThen(Completable.fromAction(this::compressBackupFile))
				.andThen(Completable.fromAction(this::sendSuccessMail))
				.blockingSubscribe();
		}
		catch (Throwable any)
		{
			sendFailureMail(any);
		}
	}

	@SuppressWarnings ("BlockingMethodInNonBlockingContext")
	private void downloadBackupFile()
	{
		MirrorSiteService.getInstance()
			.requestMirror(
				concatPagesTitles(this.pages),
				"1",
				"1",
				"+\\",
				"Special:导出页面"
			)
			.subscribeOn(Schedulers.io())
			.observeOn(Schedulers.single())
			.flatMapCompletable(res -> Completable.fromAction(() -> {
				log.info("download start");
				try (
					ResponseBody body = res.body();
					InputStream is = (body == null)
						? null
						: body.byteStream();
					OutputStream fos = (body == null)
						? null
						: Files.newOutputStream(Paths.get(outputPath))
				)
				{
					if (body == null)
					{
						throw new NullPointerException("Null body");
					}
					log.info("download connection opened.");
					byte[] buffer = new byte[this.bufferSize];
					AtomicLong downloadedSize = new AtomicLong(0);
					AtomicBoolean downloadFinished = new AtomicBoolean(false);
					Completable.fromAction(() ->
							log.info("Downloaded backup file size: {}", downloadedSize)
						)
						.repeatUntil(downloadFinished::get)
						.subscribeOn(Schedulers.newThread())
						.observeOn(Schedulers.newThread())
						.subscribe();
					try{
						while (true)
						{
							int i = is.read(buffer);
							if (i < 0)
							{
								break;
							}
							fos.write(buffer, 0, i);
							downloadedSize.addAndGet(i);
						}
					}
					finally
					{
						downloadFinished.set(true);
					}
					log.info("finish backup.");
				}
			}))
			.doOnError((th) -> {
				throw new RuntimeException("Failed to query backup.", th);
			})
			.retry(retryTimes)
			.blockingSubscribe();
	}

	private void compressBackupFile()
	{
		log.info("start compress file.");
		try (
			SevenZOutputFile outArchive =
				new SevenZOutputFile(new File(this.compressedFilePath));
			InputStream is = Files.newInputStream(Paths.get(this.outputPath))
		)
		{
			SevenZArchiveEntry entry = new SevenZArchiveEntry();
			entry.setName("backup.xml");
			Date date = new Date();
			entry.setCreationDate(date);
			entry.setAccessDate(date);
			outArchive.putArchiveEntry(entry);
			byte[] buffer = new byte[bufferSize];
			while (true)
			{
				int i = is.read(buffer);
				if (i < 0)
				{
					break;
				}
				outArchive.write(buffer, 0, i);
			}
			outArchive.closeArchiveEntry();
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to compress file", e);
		}
		log.info("finish compress file.");
	}

	private void sendSuccessMail()
	{
		log.info("sendSuccessMail()");
		List<Completable> tasks = Objects
			.requireNonNull(emailConfig.getReceivers())
			.stream()
			.map(receiver -> Completable.fromAction(() -> sendSingleMail(
					receiver.getAddress(),
					"[成功] " + Objects.requireNonNullElse(
						receiver.getMailSubject(),
						Objects.requireNonNull(emailConfig.getMailSubject())
					),
					"<h1>" + Objects.requireNonNull(emailConfig.getMailSubject())
					+ "</h1>" + "<h3>成功备份！</h3><hr/>"
					+ "<div>备份页数：" + pages.size() + "</div>"
					+ "<div>备份文件大小：" + getBackupFileSize() + " bytes</div>"
					+ "<div>备份压缩后大小：" + getCompressedFileSize() +
					" bytes</div>"
					+ "<div>备份目录磁盘可用空间：" + getDiskAvailableSpace() +
					"bytes</div>",
					generateAttachments(
						receiver.isSendAttachment(),
						receiver.isSendErrorStackTrace()
					)
				))
				.doOnError(th -> log.error(
					"Failed to send mail to\"{}\"",
					receiver.getAddress(),
					th
				))
				.onErrorComplete())
			.collect(Collectors.toList());
		Completable.merge(tasks)
			.subscribeOn(Schedulers.io())
			.observeOn(Schedulers.io())
			.doOnError(e -> log.error("Failed to send success mail.", e))
			.blockingSubscribe();
	}

	private void sendFailureMail(final Throwable reason)
	{
		log.info("sendFailureMail()", reason);
		Completable.fromAction(() -> {
				try (
					PrintStream ps =
						new PrintStream(Files.newOutputStream(Paths.get(this.exceptionTraceFilePath)))
				)
				{
					reason.printStackTrace(ps);
				}
			})
			.subscribeOn(Schedulers.io())
			.observeOn(Schedulers.io())
			.blockingSubscribe();
		List<Completable> tasks = Objects
			.requireNonNull(emailConfig.getReceivers())
			.stream()
			.map(receiver -> Completable.fromAction(() -> sendSingleMail(
					receiver.getAddress(),
					"[失败] " + Objects.requireNonNullElse(
						receiver.getMailSubject(),
						Objects.requireNonNull(emailConfig.getMailSubject())
					),
					"<h1>" + Objects.requireNonNull(emailConfig.getMailSubject())
					+ "</h1>" + "<h3>备份失败！</h3><hr/>"
					+ "<div>已检索页数：" + pages.size() + "</div>"
					+ "<div>备份文件大小：" + getBackupFileSize()
					+ "bytes</div>"
					+ "<div>备份压缩后大小：" + getCompressedFileSize()
					+ "bytes</div>"
					+ "<div>备份目录磁盘可用空间：" + getDiskAvailableSpace()
					+ "bytes</div><hr/>"
					+ "<div>异常类型：" + reason.getClass()
						.getCanonicalName() + "</div>"
					+ "<div>异常消息："
					+ Objects.requireNonNull(reason.getMessage(), "null")
					+ "</div>",
					generateAttachments(
						receiver.isSendAttachment(),
						receiver.isSendErrorStackTrace()
					)
				))
				.doOnError(th -> log.error(
					"Failed to send mail to\"{}\"",
					receiver.getAddress(),
					th
				))
				.onErrorComplete())
			.collect(Collectors.toList());
		Completable.merge(tasks)
			.subscribeOn(Schedulers.io())
			.observeOn(Schedulers.io())
			.doOnError(e -> log.error("Failed to send failure mail.", e))
			.blockingSubscribe();
	}

	private String concatPagesTitles(final Collection<String> pages)
	{
		List<String> simplifiedList = new ArrayList<>(pages);
		Collections.sort(simplifiedList);
		StringBuilder titleInLinesBuilder = new StringBuilder();
		for (String line : simplifiedList)
		{
			titleInLinesBuilder.append(line);
			titleInLinesBuilder.append("\n");
		}
		return titleInLinesBuilder.toString();
	}

	/**
	 * 发送邮件。
	 *
	 * @param attachments key = 附件文件路径; value = 附件名
	 */
	@SneakyThrows
	private void sendSingleMail(
		final String receiverAddr,
		final String subject,
		final String content,
		final Map<String, String> attachments
	)
	{
		String emailHost = Objects.requireNonNull(emailConfig.getEmailHost());
		String transportType =
			Objects.requireNonNull(emailConfig.getTransportType());
		String fromUser = Objects.requireNonNull(emailConfig.getFromUser());
		String fromEmail = Objects.requireNonNull(emailConfig.getFromEmail());
		String authCode = Objects.requireNonNull(emailConfig.getAuthCode());
		Properties props = new Properties();
		props.setProperty(
			"mail.transport.protocol",
			transportType
		);
		props.setProperty("mail.host", emailHost);
		props.setProperty("mail.user", fromUser);
		props.setProperty("mail.from", fromEmail);
		Session session = Session.getInstance(props, null);
		// session.setDebug(true);
		MimeMessage message = new MimeMessage(session);
		InternetAddress from = new InternetAddress(fromEmail);
		message.setFrom(from);
		InternetAddress to = new InternetAddress(receiverAddr);
		message.setRecipient(Message.RecipientType.TO, to);
		message.setSubject(subject);
		Multipart multipart = new MimeMultipart();
		BodyPart textPart = new MimeBodyPart();
		textPart.setContent(content, "html");
		multipart.addBodyPart(textPart);
		for (Map.Entry<String, String> entry : attachments.entrySet())
		{
			String path = Objects.requireNonNull(entry.getKey());
			String fileName = Objects.requireNonNull(entry.getValue());
			File f = new File(path);
			if (!f.isFile() || !f.canRead())
			{
				log.error("Failed to find file\"{}\"", path);
			}

			BodyPart attachmentPart = new MimeBodyPart();
			DataSource source = new FileDataSource(f);
			attachmentPart.setDataHandler(new DataHandler(source));
			attachmentPart.setFileName(fileName);
			multipart.addBodyPart(attachmentPart);
		}
		message.setContent(multipart);
		message.saveChanges();
		Transport transport = session.getTransport();
		transport.connect(emailHost, fromEmail, authCode);
		transport.sendMessage(message, message.getAllRecipients());
	}

	private long getBackupFileSize()
	{
		File f = new File(this.outputPath);
		return f.length();
	}

	private long getCompressedFileSize()
	{
		File f = new File(this.compressedFilePath);
		return f.length();
	}

	private long getDiskAvailableSpace()
	{
		return new File(this.outputPath).getUsableSpace();
	}

	private Map<String, String> generateAttachments(
		final boolean backupFile,
		final boolean errTrack
	)
	{
		Map<String, String> result = new HashMap<>();
		if (backupFile && new File(compressedFilePath).canRead())
		{
			result.put(compressedFilePath, "backup.7z");
		}
		if (errTrack && new File(exceptionTraceFilePath).canRead())
		{
			result.put(exceptionTraceFilePath, "stackTrace.txt");
		}
		return result;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public static class Builder
	{
		private Single<? extends Set<String>> backupListCollector;
		private String outputPath;
		private String compressedFilePath;
		private String exceptionTraceFilePath;
		private EmailConfig emailConfig;
		private int retryTimes = DEFAULT_RETRY_TIMES;
		private int bufferSize = DEFAULT_BUFFER_SIZE;

		public Crawler build()
		{
			return new Crawler(
				Objects.requireNonNull(backupListCollector),
				Objects.requireNonNull(outputPath),
				Objects.requireNonNull(compressedFilePath),
				Objects.requireNonNull(exceptionTraceFilePath),
				Objects.requireNonNull(emailConfig),
				retryTimes, bufferSize
			);
		}

		public Builder backupListCollector(final Single<? extends Set<String>> o)
		{
			this.backupListCollector = o;
			return this;
		}

		public Builder outputPath(final String o)
		{
			this.outputPath = o;
			return this;
		}

		public Builder compressedFilePath(final String o)
		{
			this.compressedFilePath = o;
			return this;
		}

		public Builder exceptionTraceFilePath(final String o)
		{
			this.exceptionTraceFilePath = o;
			return this;
		}

		public Builder emailConfig(final EmailConfig o)
		{
			this.emailConfig = o;
			return this;
		}

		public Builder retryTimes(final int o)
		{
			this.retryTimes = o;
			return this;
		}

		public Builder bufferSize(final int o)
		{
			this.bufferSize = o;
			return this;
		}
	}
}
