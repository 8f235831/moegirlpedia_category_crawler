package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import pers.u8f23.crawler.houbun.category.config.RootConfig;
import pers.u8f23.crawler.houbun.category.response.CategoryPageParsed;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

/**
 * @author 8f23
 * @create 2024/3/31-22:51
 */
@Slf4j
public class Main02
{
	private static RootConfig config;
	private static final String ROOT_PATH = "Category:日本漫画作品";
	private static final long MAX_WAIT_INTERVAL = 40 * 60 * 1000; //40 minutes
	private static final LongUnaryOperator WAIT_INTERVAL_UPGRADE_RULE = l -> (l << 1);

	public static void main(String[] args) throws Exception
	{
		String definedConfigFile = args.length > 0 ? args[0] : null;
		if (definedConfigFile == null)
		{
			log.info("use default config file.");
		}
		else
		{
			log.info("use assigned config file at\"{}\".", definedConfigFile);
		}
		config = readConfig(definedConfigFile);
		if (config == null)
		{
			log.error("missing config file!");
			return;
		}
		log.info("Success to load config:{}", new Gson().toJson(config));

		Set<String> worksTitles = collectAllWorks();
		log.info("Success to request all works' title. Collection size is {}.",
			worksTitles.size());
//		worksTitles.forEach(System.out::println);
		// TODO
	}

	/**
	 * 检索作品信息。
	 * 从{@code [[Category:芳文社]]}出发遍历各级子分类，各级分类下的条目名视作作品名并被收集作为结果。
	 * 此外，各级目录名也会收集至结果中（尽管这并不正确）。
	 *
	 * @return 全部所属作品名。
	 */
	private static Set<String> collectAllWorks()
	{
		LinkedHashSet<String> res = new LinkedHashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.addLast(ROOT_PATH);
		while (!queue.isEmpty())
		{
			String currCate = queue.removeFirst();
			if (res.contains(currCate))
			{
				// 无视已遍历的页面。
				continue;
			}
			res.add(currCate);

			if (!currCate.startsWith("Category:")
			    && !currCate.startsWith("/index.php?title=Category:"))
			{
				continue;
			}
			// 开始网络请求。
			Supplier<Single<CategoryPageParsed>> singleProvider = () ->
				(currCate.startsWith("Category:")
					? HomeSiteService.getInstance().get(currCate)
					: HomeSiteService.getInstance().getUrl(currCate))
					.map(CategoryPageParsed::parse);
			CategoryPageParsed cateInfos = requestWithRetry(
				singleProvider,
				() -> log.info("try to request category page <{}>.", currCate),
				() -> log.warn("failed to request category page <{}>.", currCate)
			);
			if (cateInfos == null)
			{
				cateInfos = CategoryPageParsed.EMPTY_RESULT;
			}
			queue.addAll(cateInfos.getNextPagePaths());
			queue.addAll(cateInfos.getSubCategories());
			res.addAll(cateInfos.getSubPages());
		}
		return res;
	}

	/**
	 * 读取配置文件。
	 */
	private static RootConfig readConfig(String path)
	{
		try
		{
			File file = new File((path == null || path.isEmpty())
				? "config.json"
				: path);
			String configJson = new String(Files.readAllBytes(file.toPath()));
			RootConfig config =
				new Gson().fromJson(configJson, RootConfig.class);
			String timeStr =
				new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
			config.setOutputFilePath(convertKey(
				config.getOutputFilePath(),
				timeStr
			));
			config.setOutputFilePath(convertKey(
				config.getOutputFilePath(),
				timeStr
			));
			config.setCompressedFilePath(convertKey(
				config.getCompressedFilePath(),
				timeStr
			));
			config.setExceptionTraceFilePath(convertKey(
				config.getExceptionTraceFilePath(),
				timeStr
			));
			return config;
		}
		catch (Exception e)
		{
			log.error("failed to read config. ", e);
			return null;
		}
	}

	/**
	 * 读取配置文件时，替换时间占位符为可读日期。
	 */
	private static String convertKey(String raw, String timeStr)
	{
		if (!raw.contains("%s"))
		{
			return raw;
		}
		return String.format(raw, timeStr);
	}

	/**
	 * 自适应的请求重传方法，此方法是阻塞性的，内部会执行强制性的线程等待。
	 * 每轮请求前首先要求构建请求对象{@code Single}。
	 * 如果请求成功则等待初始指定时间后返回请求结果。
	 * 如果请求失败则采用指数退避算法更新线程等待时间，在此轮等待结束后尝试进入下一轮请求。
	 * 如果等待时间已超出指定的最大阈值，则判定请求无法完成，并返回{@code null}。
	 */
	private static <R> R requestWithRetry(
		Supplier<Single<R>> requestProvider,
		Runnable beforeRequest,
		Runnable onFail)
	{
		int epoch = 0;
		long waitInterval = config.getRequestIntervalTime();
		while (waitInterval <= MAX_WAIT_INTERVAL)
		{
			try
			{
				beforeRequest.run();
				R res = requestProvider.get()
					.onErrorComplete()
					.blockingGet();
				if (res != null)
				{
					log.info(
						"Thread will sleep for [{}] milliseconds in the final epoch.",
						waitInterval
					);
					Thread.sleep(config.getRequestIntervalTime());
					return res;
				}
				onFail.run();
			}
			catch (Exception e)
			{
				log.warn(
					"Failed to request data. " +
					"Thread will sleep for [{}] milliseconds in epoch [{}].",
					waitInterval, epoch, e
				);
				try
				{
					Thread.sleep(waitInterval);
				}
				catch (InterruptedException e_)
				{
					waitInterval = Long.MAX_VALUE;
				}
			}
			epoch++;
			waitInterval = WAIT_INTERVAL_UPGRADE_RULE.applyAsLong(waitInterval);
		}
		log.error("The request failed at epoch [{}].", epoch);
		return null;
	}
}
