package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.core.Single;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * @author 8f23
 * @create 2023/7/2-17:09
 */
public interface HttpService
{
	@GET ("/{title}")
	Single<Response<ResponseBody>> get(@Path ("title") String pageTitle);

	static HttpService getInstance()
	{
		HttpService localInstance = HttpServiceHolder.instance;
		if (localInstance != null)
		{
			return localInstance;
		}
		HttpServiceHolder.instance = localInstance = HttpUtils.buildService(HttpService.class);
		return localInstance;
	}

	class HttpServiceHolder
	{
		private static HttpService instance;
	}
}

