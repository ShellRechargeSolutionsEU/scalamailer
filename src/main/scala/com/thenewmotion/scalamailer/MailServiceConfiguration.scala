package com.thenewmotion.scalamailer

import java.util.Properties

/**
 *  Configuration of the mail service, to be able to send mail with the service.
 *  Implement the trait with your own class to provide the proper details
 */
trait MailServiceConfiguration {
  def smtpHost: String
  def smtpAuth: Boolean
  def smtpStartTlsEnable: Boolean
  def user: Option[String]
  def password: Option[String]
  def runMode: Option[String]

  /**
   * Used in the mailer service to configure the service in a proper way as the Lift Mailer is used
   * The lift mailer makes use of a set of named properties
   */
  def asProperties: Properties = {
    val props = new Properties()
    props.put("mail.smtp.host", smtpHost)
    props.put("mail.smtp.auth", smtpAuth.toString)
    props.put("mail.smtp.starttls.enable", smtpStartTlsEnable.toString)
    // run.mode is a Lift specific config to mark the code running in dev, test, staging or production.
    // in test mail is not actually sent, but pushed to the logger.
    runMode map (m => props.put("run.mode", m))

    props
  }
}

