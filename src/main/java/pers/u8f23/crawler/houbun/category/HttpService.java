package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.core.Single;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.entity.ParseResponse;
import retrofit2.Response;
import retrofit2.http.Field;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * @author 8f23
 * @create 2023/7/2-17:09
 */
public interface HttpService
{
	@GET ("/api.php")
	Single<Response<ParseResponse>> getPage(@Query ("page") String pageTitle,
		@Query ("action") String action, @Query ("format") String format);

	static Single<Response<ParseResponse>> getPage(String pageTitle)
	{
		return getInstance().getPage(pageTitle, "parse", "json");
	}

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

