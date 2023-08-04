package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.response.ApiBaseResponse;
import pers.u8f23.crawler.houbun.category.response.Query;
import retrofit2.Response;

import java.io.FileOutputStream;
import java.io.PrintStream;
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
	private final String outputFilePath;
	private final String outputSimplifiedFilePath;
	@Getter
	private volatile boolean mainPagesFinished = false;


	public Crawler(
		@NonNull String rootCateTitle,
		@NonNull String outputFilePath,
		@NonNull String outputSimplifiedFilePath
	)
	{
		this.outputFilePath = outputFilePath;
		this.outputSimplifiedFilePath = outputSimplifiedFilePath;
		requestRootCate(rootCateTitle, rootCateTitle);
		Completable.fromAction(this::afterMainPages)
			.delay(1, TimeUnit.SECONDS)
			.repeatUntil(() -> mainPagesFinished)
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
			single.subscribeOn(Schedulers.single())
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
				.subscribeOn(Schedulers.single())
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
		try (FileOutputStream fos = new FileOutputStream(this.outputFilePath))
		{
			fos.write(gson.toJson(this.resultSet).getBytes());
		}
		Set<String> simplifiedSet = new HashSet<>();
		this.resultSet.forEach((k, v) -> {
			simplifiedSet.add(k);
			simplifiedSet.addAll(v);
		});
		List<String> simplifiedList = new ArrayList<>(simplifiedSet);
		Collections.sort(simplifiedList);
		try (PrintStream ps = new PrintStream(this.outputSimplifiedFilePath))
		{
			for (String line : simplifiedList)
			{
				ps.println(line);
			}
		}
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
			.subscribeOn(Schedulers.single())
			.observeOn(Schedulers.computation())
			.map(HttpUtils::parseResponse)
			.map(ApiBaseResponse::getQuery)
			.map(Query::flat)
			.doOnSuccess(map -> map.forEach((k, vs) -> vs.forEach(v ->
				{
					resultSet.computeIfAbsent(k, k1 -> new HashSet<>()).add(v);
					log.debug("query creators result:[{}, {}]", k, v);
				}
			)))
			.doAfterTerminate(requestingCounter::decrementAndGet)
			.retry(RETRY_TIMES)
			.subscribe();
	}
}
