package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.config.RootConfig;
import pers.u8f23.crawler.houbun.category.response.ApiBaseResponse;
import pers.u8f23.crawler.houbun.category.response.CategoryPageParsed;
import pers.u8f23.crawler.houbun.category.response.Query;
import retrofit2.Response;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author 8f23
 * @create 2024/3/31-22:51
 */
@Slf4j
public class Main02
{
	private static RootConfig config;
	private static final String ROOT_PATH = "Category:芳文社";
	private static final long MAX_WAIT_INTERVAL = 40 * 60 * 1000; //40 minutes
	private static final LongUnaryOperator WAIT_INTERVAL_UPGRADE_RULE = l -> (l << 1);

	public static void main(String[] args)
	{
		// 读取配置文件。
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

		if (config.isPrintExceptionsToLog())
		{
			RxJavaPlugins.setErrorHandler((th) -> log.info(
				"global rxjava error catch: ",
				th
			));
		}

		// 请求作品名。
		log.info("Start to request titles.");
		Set<String> worksTitles = new LinkedHashSet<>();
		traverseCategory(ROOT_PATH, worksTitles);
		worksTitles.removeIf(title -> title.startsWith("/index.php?title=Category:"));
		log.info("Success to request all titles. Collection size is [{}].",
			worksTitles.size());

		// 请求各作品下页面。
		log.info("Start to request normal pages under work categories.");
		Set<String> normalPages = new LinkedHashSet<>(worksTitles);
		String[] titleArray = worksTitles.toArray(new String[0]);
		for (int i = 0; i < titleArray.length; i++)
		{
			String category = "Category:" + titleArray[i];
			log.info("Start to pages under category <{}>. Task progress: [{} / {}]",
				category, i + 1, titleArray.length);
			traverseCategory(category, normalPages);
			log.info("Start to request creators of work <{}>. ", category);
			Set<String> creators = requestAuthor(category);
			normalPages.addAll(creators);
			log.info("Succeed to find [{}] creators of work <{}>. ", creators.size() , category);
		}
		normalPages.removeIf(title -> title.startsWith("/index.php?title=Category:"));
		log.info("Success to request all normal pages. Page collection size is [{}].",
			normalPages.size());
		normalPages.forEach(System.out::println);
	}

	/**
	 * 检索作品信息。
	 * 从参数{@code rootCategory}指定的根分类页面出发，深度遍历各级子分类页面，
	 * 各级分类下的条目名视作结果被添加到集合{@code result}中。
	 * 此外，为了保证不发起重复的请求，各级分类名及分页请求路径也会被收集至结果中，用于筛除已访问的页面。
	 * 因此，需要在此方法执行完之后，清除全部形如{@code ^/index\.php.*}的数据项。
	 */
	private static void traverseCategory(String rootCategory, Set<String> result)
	{
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.addLast(rootCategory);
		while (!queue.isEmpty())
		{
			String currCate = queue.removeLast();
			if (result.contains(currCate))
			{
				// 无视已遍历的页面。
				continue;
			}
			if (!currCate.startsWith("Category:")
			    && !currCate.startsWith("/index.php?title=Category:"))
			{
				continue;
			}

			// 开始网络请求。
			Supplier<CategoryPageParsed> request = () -> mapCategoryRequest(currCate)
				.map(CategoryPageParsed::parse)
				.onErrorComplete()
				.doOnError(e -> log.error("request err", e))
				.blockingGet();
			CategoryPageParsed cateInfos = requestWithRetry(
				request,
				() -> log.info("try to request category page <{}>.", currCate),
				() -> log.warn("failed to request category page <{}>.", currCate)
			);
			cateInfos = cateInfos != null ? cateInfos : CategoryPageParsed.EMPTY_RESULT;
			queue.addAll(cateInfos.getNextPagePaths());
			queue.addAll(cateInfos.getSubCategories());
			result.addAll(cateInfos.getSubPages());
		}
	}


	/**
	 * 自适应的请求重传方法，此方法是阻塞性的，内部会执行强制性的线程等待。
	 * 如果请求成功则等待初始指定时间后返回请求结果。
	 * 如果请求失败则采用指数退避算法更新线程等待时间，在此轮等待结束后尝试进入下一轮请求。
	 * 如果等待时间已超出指定的最大阈值，则判定请求无法完成，并返回{@code null}。
	 *
	 * @param <R>           返回结果的类型。
	 * @param request       请求方法，此方法应当是可重复的。
	 * @param beforeRequest 每轮请求前执行此方法。
	 * @param onFail        每轮请求失败后执行此方法。
	 */
	private static <R> R requestWithRetry(
		Supplier<R> request,
		Runnable beforeRequest,
		Runnable onFail)
	{
		int epoch = 0;
		long waitInterval = config.getRequestIntervalTime();
		while (waitInterval <= MAX_WAIT_INTERVAL)
		{
			try
			{
				// 执行
				beforeRequest.run();
				R res = request.get();
				if (res != null)
				{
					log.info(
						"Thread will sleep for [{}] milliseconds in the final epoch.",
						config.getRequestIntervalTime()
					);
					Thread.sleep(config.getRequestIntervalTime());
					return res;
				}
			}
			catch (Exception ignored)
			{

			}
			onFail.run();
			log.warn(
				"Failed to request data. " +
				"Thread will sleep for [{}] milliseconds in epoch [{}].",
				waitInterval, epoch
			);
			try
			{
				Thread.sleep(waitInterval);
			}
			catch (InterruptedException e)
			{
				waitInterval = Long.MAX_VALUE;
				continue;
			}
			epoch++;
			waitInterval = WAIT_INTERVAL_UPGRADE_RULE.applyAsLong(waitInterval);
		}
		log.error("The request failed at epoch [{}].", epoch);
		return null;
	}

	private static Set<String> requestAuthor(String title)
	{
		Supplier<Set<String>> request = () -> {
			Response<ApiBaseResponse<Query>> raw = HomeSiteService
				.getInstance()
				.getCategories(title)
				.onErrorComplete()
				.blockingGet();
			if (raw == null)
			{
				throw new RuntimeException();
			}
			if (raw.code() == 404)
			{
				return Collections.emptySet();
			}
			return Query.flat(Objects.requireNonNull(raw.body())
					.getQuery())
				.values()
				.stream()
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
		};
		Set<String> result = requestWithRetry(
			request,
			() -> log.info("try to request categories of page <{}>.", title),
			() -> log.warn("failed to request categories of page <{}>.", title)
		);
		return result != null ? result : Collections.emptySet();
	}

	private static Single<Response<ResponseBody>> mapCategoryRequest(String title)
	{
		return title.startsWith("Category:")
			? HomeSiteService.getInstance().get(title)
			: HomeSiteService.getInstance().getUrl(title);
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
}
