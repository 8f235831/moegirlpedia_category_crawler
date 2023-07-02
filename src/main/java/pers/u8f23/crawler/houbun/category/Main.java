package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.IOException;

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
		HttpService.getInstance()
			.get("Category:芳文社")
			.subscribeOn(Schedulers.io())
			.observeOn(Schedulers.newThread())
			.subscribe(new DisposableSingleObserver<Response<ResponseBody>>()
			{
				@Override
				public void onSuccess(@NonNull Response<ResponseBody> responseBodyResponse)
				{
					log.info("onSuccess");
					ResponseBody responseBody = responseBodyResponse.body();
					if (responseBody == null)
					{
						log.info("responseBody == null");
						return;
					}
					try
					{
						String body = responseBody.string();
						log.info("{}", body);
					}
					catch (IOException e)
					{
						log.info("body read failed", e);
					}
				}

				@Override
				public void onError(@NonNull Throwable e)
				{
					log.info("onError", e);
				}
			});
		Thread.sleep(Long.MAX_VALUE);
	}
}
