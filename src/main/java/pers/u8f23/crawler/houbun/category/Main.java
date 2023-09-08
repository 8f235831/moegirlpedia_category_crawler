package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.extern.slf4j.Slf4j;
import pers.u8f23.crawler.houbun.category.config.RootConfig;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 入口主函数。
 *
 * @author 8f23
 * @create 2023/4/15-11:57
 */
@Slf4j
public class Main
{
	public static void main(String[] args) throws InterruptedException
	{
		RxJavaPlugins.setErrorHandler((th) -> log.info(
			"global rxjava error " + "catch: ",
			th
		));
		RootConfig config = readConfig(args.length > 0 ? args[0] : null);
		if(config == null)
		{
			log.error("missing config file!");
			return;
		}
		log.info("Success to load config:{}", new Gson().toJson(config));
		new CrawlerD(config);
		Thread.sleep(Long.MAX_VALUE);
	}

	private static RootConfig readConfig(String path)
	{
		try
		{
			File file = new File((path == null || path.isEmpty())
				? "config.json"
				: path);
			// log.info("config path:{}", file.getAbsolutePath());
			String configJson = new String(Files.readAllBytes(file.toPath()));
			RootConfig config =
				new Gson().fromJson(configJson, RootConfig.class);
			String timeStr =
				new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
			config.setOutputFilePath(convertKey(
				config.getOutputFilePath(),
				timeStr
			));
			config.setOutputSimplifiedFilePath(convertKey(
				config.getOutputSimplifiedFilePath(),
				timeStr
			));
			config.setBackupFilePath(convertKey(
				config.getBackupFilePath(),
				timeStr
			));
			return config;
		}
		catch(Exception e)
		{
			log.error("failed to read config. ", e);
			return null;
		}
	}

	private static String convertKey(String raw, String timeStr)
	{
		if(!raw.contains("%s"))
		{
			return raw;
		}
		return String.format(raw, timeStr);
	}
}
