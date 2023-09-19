package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.response.ApiBaseResponse;
import pers.u8f23.crawler.houbun.category.response.Query;
import retrofit2.Response;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author 8f23
 * @create 2023/9/11-10:42
 */
@Slf4j
public class HoubunCollector
{
	private static final String ROOT_PATH = "Category:芳文社";

	private final int retryTimes;
	private final Set<String> pages = new HashSet<>();
	private final Set<String> visited = new HashSet<>();
	private final Set<String> activeTasks = new HashSet<>();

	public HoubunCollector(final int retryTimes)
	{
		this.retryTimes = retryTimes;

		// add root.
		pages.add(ROOT_PATH);
	}

	public Flowable<String> flowable()
	{
		return Flowable.create(
				(FlowableOnSubscribe<String>) emitter ->
					submitTask(new Task(ROOT_PATH, true, false), emitter),
				BackpressureStrategy.BUFFER
			)
			.doOnComplete(() -> log.info("All data from houbun are collected!"));
	}

	public Single<? extends Set<String>> single()
	{
		return flowable()
			.toList()
			.map(HashSet::new);
	}


	private void submitTask(Task task, FlowableEmitter<String> emitter)
	{
		log.info("submit task:\"{}\"[{}]", task.path, task.inRootCate);
		String path = task.path;
		if (path == null || path.startsWith("User:") || accessLockedField(() ->
			visited.contains(path) || activeTasks.contains(path)
		))
		{
			return;
		}
		if (path.startsWith("Category:")
		    || path.startsWith("index.php?title=Category:"))
		{
			boolean writeSuccess = accessLockedField(() -> {
				boolean contained =
					visited.contains(path) || activeTasks.contains(path);
				if (contained)
				{
					return false;
				}
				activeTasks.add(path);
				return true;
			});
			if (!writeSuccess)
			{
				return;
			}
			Single<Response<ResponseBody>> single =
				path.startsWith("Category:")
					? HomeSiteService.getInstance()
					.get(path)
					: HomeSiteService.getInstance()
						.getUrl(path);
			single.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.computation())
				.map(HttpUtils::parseRawHtmlCategoryPage)
				.retry(retryTimes)
				.doOnSuccess(set -> {
					set.forEach(i -> {
						submitTask(
							new Task(
								i,
								task.inRootCate,
								false
							), emitter);
						emitter.onNext(i);
					});
					accessLockedField(() -> pages.addAll(set));
				})
				.doFinally(() -> accessLockedField(() -> {
					visited.add(path);
					activeTasks.remove(path);
					if (activeTasks.isEmpty())
					{
						emitter.onComplete();
					}
					return 0;
				}))
				.subscribe();
		}
		else if (task.inRootCate)
		{
			// traverse works.
			submitTask(new Task("Category:" + path, false, false), emitter);
			submitTask(new Task(path, false, true), emitter);
		}
		else if (task.workRoot)
		{
			// query creators.
			boolean writeSuccess = accessLockedField(() -> {
				boolean contained =
					visited.contains(path) || activeTasks.contains(path);
				if (contained)
				{
					return false;
				}
				activeTasks.add(path);
				return true;
			});
			if (!writeSuccess)
			{
				return;
			}
			HomeSiteService.getInstance()
				.getCategories(task.path)
				.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.computation())
				.map(HttpUtils::parseResponse)
				.map(ApiBaseResponse::getQuery)
				.map(Query::flat)
				.map(map -> map.values()
					.stream()
					.reduce((a, b) -> {
						a.addAll(b);
						return a;
					})
					.orElseThrow(() ->
						new RuntimeException("no element!")
					))
				.retry(retryTimes)
				.doOnSuccess(set -> accessLockedField(() -> {
					pages.addAll(set);
					set.forEach(emitter::onNext);
					return "";
				}))
				.doFinally(() -> accessLockedField(() -> {
					visited.add(path);
					activeTasks.remove(path);
					log.info("[{}] task(s) uncompleted.", activeTasks.size());
					if (activeTasks.isEmpty())
					{
						emitter.onComplete();
					}
					return 0;
				}))
				.subscribe();
		}
	}

	private synchronized <R> R accessLockedField(Supplier<R> action)
	{
		return action.get();
	}

	@AllArgsConstructor
	private static class Task
	{
		private final String path;
		private boolean inRootCate;
		private boolean workRoot;
	}
}
