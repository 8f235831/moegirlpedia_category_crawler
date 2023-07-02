package pers.u8f23.crawler.houbun.category.core;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * @author 8f23
 * @create 2023/7/2-18:48
 */
@Slf4j
public final class TaskObserver<T> implements SingleObserver<T>
{
	private final Consumer<T> onSuccess;
	private final Runnable onFinish;

	public TaskObserver(
		@NonNull Consumer<T> onSuccess,
		@NonNull Runnable onCreate,
		@NonNull Runnable onFinish
	)
	{
		onCreate.run();
		this.onSuccess = onSuccess;
		this.onFinish = onFinish;
	}

	@Override
	public void onSubscribe(@NonNull Disposable d)
	{
		// nop
	}

	@Override
	public void onSuccess(@NonNull T t)
	{
		onSuccess.accept(t);
		onFinish.run();
	}

	@Override
	public void onError(@NonNull Throwable e)
	{
		onFinish.run();
	}
}
