package com.thenewmotion.scalamailer

import java.util.Properties
import java.io.InputStream
import javax.mail._
import internet.{MimeMultipart, MimeMessage, MimeBodyPart}
import scala.collection.mutable.ArrayBuffer
import concurrent.{ExecutionContext, Future}
import net.liftweb.util.Mailer
import net.liftweb.common.{Full, Loggable}
import scala.util.{Try, Failure, Success}

/**
 * ScalaMailerService is a service that allows to send email via SMTP hosts.
 * This includes google mail (authorization and tls enabled smtp hosts)
 *
 * In order to use the service, provide a [[com.thenewmotion.scalamailer.MailServiceConfiguration]] to initiate this class
 * Thereafter you can use sendMail with a timeout or without a timeout to send mail.
 * This MailService is an extension of the lift-util (http://liftweb.net) Mailer.
 *
 * Adds support of [[com.thenewmotion.scalamailer.ScalaMailerService.HTMLMailBodyType]]
 * to mailer for sending html which is not valid xml and file attachments
 * Configurable via the [[com.thenewmotion.scalamailer.MailServiceConfiguration]]
 */
class ScalaMailerService(val configuration: MailServiceConfiguration) extends Mailer with Loggable {

  // set authenticator if user and password properties are provided
  (configuration.user, configuration.password) match {
    case (Some(user), Some(password)) => {
      this.authenticator = Full(new Authenticator {
        override def getPasswordAuthentication(): PasswordAuthentication = {
          new PasswordAuthentication(user, password)
        }
      })
    }
    case _ => logger.warn("no user/password provided")
  }

  // Override for Lift Mailer compatibility
  override lazy val properties: Properties = configuration.asProperties

  /**
   * HTML style mail body that is not seen as proper XML.
   * @param content a string containing the HTML to be sent.
   */
  final case class HTMLMailBodyType(content: String) extends MailBodyType

  /**
   * @param inputStream should return a new input stream pointing to the beginning of the stream each time
   * as described in [[javax.activation.DataSource.getInputStream()]]
   */
  final case class FileAttachmentBodyType(fileName: String, inputStream: () => InputStream, mimeType: String) extends MailBodyType

  /**
   * Exception returned when sendMail fails.
   * @param msg the message that sendMail did fail
   * @param ex cause exception.
   */
  case class SendMailException(msg: String, ex: Throwable) extends Exception(msg, ex)

  /**
   * Send a mail
   *
   * @param from From address of the sender
   * @param subject of the mail
   * @param rest [[net.liftweb.util.Mailer.MailTypes]] actual message with all details like content, to, cc, attachments etc.
   * @param timeout amount of seconds to wait till the message is considered to be not delivered.
   * @return the [[scala.util.Success]] or [[scala.util.Failure]] of the message send. Sending mail failures are wrapped in a SendMailException.
   */
  def sendMail(from: From, subject: Subject,rest: List[MailTypes], timeout: Int): Try[Unit] = {
    import ExecutionContext.Implicits.global
    Try {
      Future(msgSendImpl(from, subject, rest), timeout) onComplete({
        case Success(r) => Success()
        case Failure(ex) => { Failure(SendMailException("Could not send mail", ex)) }
      })
    }
  }

  /* IMPLEMENTATION UNDERNEATH */

  /* override to deal with the additional body types */
  override protected def buildMailBody(tab: MailBodyType): BodyPart = {
    tab match {
      case HTMLMailBodyType(content) => {
        val bodyPart = new MimeBodyPart
        bodyPart.setContent(content, "text/html; charset=" + charSet)
        bodyPart
      }
      case FileAttachmentBodyType(fileName, inputStream, mimeType) => {
        val bodyPart = new MimeBodyPart
        bodyPart.setDataHandler(new javax.activation.DataHandler(new javax.activation.DataSource {
          override def getContentType = mimeType
          override def getInputStream = inputStream()
          override def getName = fileName
          override def getOutputStream = throw new java.io.IOException("Unable to write to item")
        }))
        bodyPart.setFileName(fileName)
        bodyPart
      }
      case _ => super.buildMailBody(tab)
    }
  }

  /* mostly copied from Lift mailer - only code for multipart sending was altered */
  override def msgSendImpl(from: From, subject: Subject, info: List[MailTypes]) {
    val session = authenticator match {
      case Full(a) => jndiSession openOr Session.getInstance(buildProps, a)
      case _ => jndiSession openOr Session.getInstance(buildProps)
    }

    val message = new MimeMessage(session)
    message.setFrom(from)
    message.setRecipients(Message.RecipientType.TO, info.flatMap {case x: To => Some[To](x) case _ => None})
    message.setRecipients(Message.RecipientType.CC, info.flatMap {case x: CC => Some[CC](x) case _ => None})
    message.setRecipients(Message.RecipientType.BCC, info.flatMap {case x: BCC => Some[BCC](x) case _ => None})
    // message.setReplyTo(filter[MailTypes, ReplyTo](info, {case x @ ReplyTo(_) => Some(x); case _ => None}))
    message.setReplyTo(info.flatMap {case x: ReplyTo => Some[ReplyTo](x) case _ => None})
    message.setSubject(subject.subject)
    info.foreach {
      case MessageHeader(name, value) => message.addHeader(name, value)
      case _ =>
    }

    val bodyTypes = info.flatMap {case x: MailBodyType => Some[MailBodyType](x); case _ => None}
    bodyTypes match {
      case PlainMailBodyType(txt) :: Nil => message.setText(txt)
      case _ => setMultipartContent(message, bodyTypes)
    }

    this.performTransportSend(message)
  }

  private def setMultipartContent(message: MimeMessage, bodyTypes: List[MailBodyType]) {
    val attachments = new ArrayBuffer[FileAttachmentBodyType]()
    val alt = new MimeMultipart("alternative")
    bodyTypes.foreach {
      bodyType =>
        bodyType match {
          case attachment: FileAttachmentBodyType => attachments += attachment
          case _ => alt.addBodyPart(buildMailBody(bodyType))
        }
    }
    if (attachments.isEmpty) {
      message.setContent(alt)
    } else {
      val mixed = new MimeMultipart("mixed")
      val wrap = new MimeBodyPart()
      wrap.setContent(alt)
      mixed.addBodyPart(wrap)
      attachments.foreach {
        attachment =>
          mixed.addBodyPart(buildMailBody(attachment))
      }
      message.setContent(mixed)
    }
  }

  /* fix by yklymko, do not know whether it's needed here */
  override protected lazy val msgSender = new MsgSender {
    override protected def messageHandler = {
      Thread.currentThread().setContextClassLoader(this.getClass.getClassLoader)
      super.messageHandler
    }
  }
}
