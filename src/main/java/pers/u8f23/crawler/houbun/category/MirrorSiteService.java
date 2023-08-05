package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.core.Single;
import lombok.Getter;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * @author 8f23
 * @create 2023/8/5-9:29
 */
public interface MirrorSiteService
{
	@POST ("/Special:%E5%AF%BC%E5%87%BA%E9%A1%B5%E9%9D%A2")
	@FormUrlEncoded
	Single<Response<ResponseBody>> requestMirror(
		@Field ("pages") String pages,
		@Field ("templates") String templates,
		@Field ("wpDownload") String wpDownload,
		@Field ("wpEditToken") String wpEditToken,
		@Field ("title") String title
		);

	static MirrorSiteService getInstance()
	{
		return ServiceHolder.getInstance();
	}

	class ServiceHolder
	{
		@Getter (lazy = true)
		private final static MirrorSiteService instance =
			HttpUtils.buildMirrorSiteService(MirrorSiteService.class);
	}
}
