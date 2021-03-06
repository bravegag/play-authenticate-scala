package controllers

import javax.inject._
import actions.{NoCacheAction, TryCookieAuthAction, WithJContextSupportAction}
import akka.stream.Materializer
import be.objectify.deadbolt.scala.DeadboltActions
import com.feth.play.module.pa.PlayAuthenticate
import constants.{FlashKey, SecurityRoleKey}
import generated.Tables.UserRow
import org.webjars.play.WebJarsUtil
import play.api.{Configuration, Environment}
import play.api.i18n._
import play.api.mvc.{InjectedController, PlayBodyParsers}
import play.core.j.JavaHelpers
import providers.{MyAuthProvider, MySignupAuthUser}
import services.UserService
import support.LangLookupSupport
import views.form._

import scala.concurrent._
import ExecutionContext.Implicits.global

@Singleton
class Account @Inject() (implicit
                         config: Configuration,
                         env: Environment,
                         mat: Materializer,
                         linkView: views.html.account.link,
                         unverifiedView: views.html.account.unverified,
                         passwordChangeView: views.html.account.password_change,
                         askLinkView: views.html.account.ask_link,
                         askMergeView: views.html.account.ask_merge,
                         webJarUtil: WebJarsUtil,
                         deadbolt: DeadboltActions,
                         auth: PlayAuthenticate,
                         userService: UserService,
                         authProvider: MyAuthProvider,
                         formContext: FormContext,
                         bodyParsers: PlayBodyParsers) extends InjectedController with I18nSupport with LangLookupSupport {
  //-------------------------------------------------------------------
  // public
  //-------------------------------------------------------------------
  def link =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.SubjectPresent()() { implicit authRequest =>
            Future {
              Ok(linkView(userService, auth))
            }
          }
        }
      }
    }

  //-------------------------------------------------------------------
  def verifyEmail =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.Restrict(List(Array(SecurityRoleKey.USER_ROLE.toString)))() { implicit authRequest =>
            Future {
              // TODO: change because this is cowboy style
              val Some(user) = userService.findInSession(jContext.session)
              val tuple =
                if (user.emailValidated) {
                  // email has been validated already
                  (FlashKey.FLASH_MESSAGE_KEY -> messagesApi("playauthenticate.verify_email.error.already_validated"))
                } else if (user.email != null && !user.email.trim.isEmpty) {
                  authProvider.sendVerifyEmailMailingAfterSignup(user, jContext)
                  (FlashKey.FLASH_MESSAGE_KEY -> messagesApi("playauthenticate.verify_email.message.instructions_sent", user.email))
                } else {
                  (FlashKey.FLASH_MESSAGE_KEY -> messagesApi("playauthenticate.verify_email.error.set_email_first", user.email))
                }

              Redirect(routes.Application.profile).flashing(tuple)
            }
          }
        }
      }
    }

  //-------------------------------------------------------------------
  def changePassword =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.Restrict(List(Array(SecurityRoleKey.USER_ROLE.toString)))() { implicit authRequest =>
            Future {
              // TODO: change because this is cowboy style
              val Some(user) = userService.findInSession(jContext.session)
              val result =
                if (!user.emailValidated) {
                  Ok(unverifiedView(userService))

                } else {
                  Ok(passwordChangeView(userService, formContext.passwordChangeForm.Instance))
                }
              result
            }
          }
        }
      }
    }

    //-------------------------------------------------------------------
    def doChangePassword =
      NoCacheAction {
        WithJContextSupportAction { implicit jContext =>
          TryCookieAuthAction {
            deadbolt.Restrict(List(Array(SecurityRoleKey.USER_ROLE.toString)))() { implicit authRequest =>
              Future {
                formContext.passwordChangeForm.Instance.bindFromRequest.fold(
                  formWithErrors => {
                    // user did not select whether to link or not link
                    BadRequest(passwordChangeView(userService, formWithErrors))
                  },
                  formSuccess => {
                    val Some(user: UserRow) = userService.findInSession(jContext.session)
                    val newPassword = formSuccess.password
                    userService.changePassword(user, new MySignupAuthUser(newPassword), true)
                    Redirect(routes.Application.profile).flashing(
                      FlashKey.FLASH_MESSAGE_KEY -> messagesApi("playauthenticate.change_password.success")
                    )
                  })
              }
            }
          }
        }
      }

  //-------------------------------------------------------------------
  def askLink =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.SubjectPresent()() { implicit authRequest =>
            Future {
              Option(auth.getLinkUser(jContext.session)) match {
                case Some(user) => Ok(askLinkView(userService, formContext.acceptForm.Instance, user))
                case None => {
                  // account to link could not be found, silently redirect to login
                  Redirect(routes.Application.index)
                }
              }
            }
          }
        }
      }
    }

  //-------------------------------------------------------------------
  def doLink =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.SubjectPresent()() { implicit authRequest =>
            Future {
              Option(auth.getLinkUser(jContext.session)) match {
                case Some(user) => {
                  formContext.acceptForm.Instance.bindFromRequest.fold(
                    formWithErrors => BadRequest(askLinkView(userService, formWithErrors, user)),
                    formSuccess => {
                      // user made a choice :)
                      val link = formSuccess.accept
                      val result = JavaHelpers.createResult(jContext, auth.link(jContext, link))
                      link match {
                        case true => result.flashing(FlashKey.FLASH_MESSAGE_KEY -> messagesApi("playauthenticate.accounts.link.success"))
                        case false => result
                      }
                    }
                  )
                }
                case None => {
                  // account to link could not be found, silently redirect to login
                  Redirect(routes.Application.index)
                }
              }
            }
          }
        }
      }
    }

  //-------------------------------------------------------------------
  def askMerge =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.SubjectPresent()() { implicit authRequest =>
            Future {
              // this is the currently logged in user
              val userA = auth.getUser(jContext.session)

              // this is the user that was selected for a login
              Option(auth.getMergeUser(jContext.session)) match {
                case Some(userB) => {
                  // you could also get the local user object here via
                  // User.findByAuthUserIdentity(newUser)
                  Ok(askMergeView(userService, formContext.acceptForm.Instance, userA, userB))
                }
                case None => {
                  // user to merge with could not be found, silently redirect to login
                  Redirect(routes.Application.index)
                }
              }
            }
          }
        }
      }
    }

  //-------------------------------------------------------------------
  def doMerge =
    NoCacheAction {
      WithJContextSupportAction { implicit jContext =>
        TryCookieAuthAction {
          deadbolt.SubjectPresent()() { implicit authRequest =>
            Future {
              // this is the currently logged in user
              val userA = auth.getUser(jContext.session)

              // this is the user that was selected for a login
              Option(auth.getMergeUser(jContext.session)) match {
                case Some(userB) => {
                  val filledForm = formContext.acceptForm.Instance.bindFromRequest
                  if (filledForm.hasErrors) {
                    // user did not select whether to merge or not merge
                    BadRequest(askMergeView(userService, filledForm, userA, userB))

                  } else {
                    // user made a choice :)
                    val merge = filledForm.get.accept
                    val result = JavaHelpers.createResult(jContext, auth.merge(jContext, merge))
                    merge match {
                      case true => result.flashing(FlashKey.FLASH_MESSAGE_KEY -> messagesApi("playauthenticate.accounts.merge.success"))
                      case false => result
                    }
                  }
                }
                case None => {
                  // user to merge with could not be found, silently redirect to login
                  Redirect(routes.Application.index)
                }
              }
            }
          }
        }
      }
    }
}