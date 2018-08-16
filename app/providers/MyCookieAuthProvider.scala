package providers

import com.feth.play.module.pa.PlayAuthenticate
import javax.inject.{Inject, Singleton}
import play.inject.ApplicationLifecycle
import com.feth.play.module.pa.providers.cookie.{CookieAuthProvider, CookieAuthUser}
import com.feth.play.module.pa.user.AuthUser

@Singleton
class MyCookieAuthProvider @Inject()(implicit
                                     auth: PlayAuthenticate,
                                     lifecycle: ApplicationLifecycle) extends CookieAuthProvider(auth, lifecycle) {

  override def save(cookieAuthUser: CookieAuthUser, loginUser: AuthUser): Unit = ???

  override def deleteSeries(authUser: AuthUser, series: String): Unit = ???

  override def check(cookieAuthUser: CookieAuthUser): CookieAuthProvider.CheckResult = ???

  override def renew(cookieAuthUser: CookieAuthUser, newToken: String): Unit = ???
}