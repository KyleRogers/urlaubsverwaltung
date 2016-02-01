
package org.synyx.urlaubsverwaltung.core.mail;

import org.apache.velocity.app.VelocityEngine;

import org.joda.time.DateMidnight;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.jvnet.mock_javamail.Mailbox;

import org.mockito.Mockito;

import org.springframework.mail.javamail.JavaMailSenderImpl;

import org.synyx.urlaubsverwaltung.core.account.domain.Account;
import org.synyx.urlaubsverwaltung.core.application.domain.Application;
import org.synyx.urlaubsverwaltung.core.application.domain.ApplicationComment;
import org.synyx.urlaubsverwaltung.core.application.domain.VacationType;
import org.synyx.urlaubsverwaltung.core.department.DepartmentService;
import org.synyx.urlaubsverwaltung.core.overtime.Overtime;
import org.synyx.urlaubsverwaltung.core.overtime.OvertimeAction;
import org.synyx.urlaubsverwaltung.core.overtime.OvertimeComment;
import org.synyx.urlaubsverwaltung.core.period.DayLength;
import org.synyx.urlaubsverwaltung.core.person.MailNotification;
import org.synyx.urlaubsverwaltung.core.person.Person;
import org.synyx.urlaubsverwaltung.core.person.PersonService;
import org.synyx.urlaubsverwaltung.core.settings.Settings;
import org.synyx.urlaubsverwaltung.core.settings.SettingsService;
import org.synyx.urlaubsverwaltung.core.sicknote.SickNote;
import org.synyx.urlaubsverwaltung.core.sync.absence.Absence;
import org.synyx.urlaubsverwaltung.test.TestDataCreator;

import java.io.IOException;

import java.math.BigDecimal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author  Aljona Murygina
 */
public class MailServiceIntegrationTest {

    private MailServiceImpl mailService;
    private PersonService personService;
    private DepartmentService departmentService;

    private Person person;
    private Person boss;
    private Person departmentHead;
    private Person secondStage;
    private Person office;
    private Application application;
    private Settings settings;

    @Before
    public void setUp() {

        Properties velocityProperties = new Properties();
        velocityProperties.put("resource.loader", "class");
        velocityProperties.put("class.resource.loader.class",
            "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityProperties.put("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.Log4JLogChute");
        velocityProperties.put("runtime.log.logsystem.log4j.logger", MailServiceIntegrationTest.class.getName());

        VelocityEngine velocityEngine = new VelocityEngine(velocityProperties);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        personService = Mockito.mock(PersonService.class);
        departmentService = Mockito.mock(DepartmentService.class);

        SettingsService settingsService = Mockito.mock(SettingsService.class);

        mailService = new MailServiceImpl(mailSender, velocityEngine, personService, departmentService, settingsService,
                "localhorscht/");

        DateMidnight now = DateMidnight.now();

        person = TestDataCreator.createPerson("user", "Lieschen", "Müller", "lieschen@muster.de");

        application = new Application();
        application.setPerson(person);
        application.setVacationType(TestDataCreator.getVacationType(VacationType.HOLIDAY));
        application.setDayLength(DayLength.FULL);
        application.setApplicationDate(now);
        application.setStartDate(now);
        application.setEndDate(now);
        application.setApplier(person);

        settings = new Settings();
        settings.getMailSettings().setActive(true);
        Mockito.when(settingsService.getSettings()).thenReturn(settings);

        // BOSS
        boss = TestDataCreator.createPerson("boss", "Hugo", "Boss", "boss@muster.de");

        Mockito.when(personService.getPersonsWithNotificationType(MailNotification.NOTIFICATION_BOSS))
            .thenReturn(Collections.singletonList(boss));

        // DEPARTMENT HEAD
        departmentHead = TestDataCreator.createPerson("head", "Michel", "Mustermann", "head@muster.de");

        Mockito.when(personService.getPersonsWithNotificationType(MailNotification.NOTIFICATION_DEPARTMENT_HEAD))
            .thenReturn(Collections.singletonList(departmentHead));

        Mockito.when(departmentService.isDepartmentHeadOfPerson(Mockito.eq(departmentHead), Mockito.any(Person.class)))
            .thenReturn(true);

        // SECOND STAGE AUTHORITY
        secondStage = TestDataCreator.createPerson("manager", "Kai", "Schmitt", "manager@muster.de");

        Mockito.when(personService.getPersonsWithNotificationType(MailNotification.NOTIFICATION_SECOND_STAGE_AUTHORITY))
            .thenReturn(Collections.singletonList(secondStage));

        Mockito.when(departmentService.isSecondStageAuthorityOfPerson(Mockito.eq(secondStage),
                    Mockito.any(Person.class)))
            .thenReturn(true);

        // OFFICE
        office = TestDataCreator.createPerson("office", "Marlene", "Muster", "office@muster.de");

        Mockito.when(personService.getPersonsWithNotificationType(MailNotification.NOTIFICATION_OFFICE))
            .thenReturn(Collections.singletonList(office));
    }


    @After
    public void tearDown() {

        Mailbox.clearAll();
    }


    @Test
    public void ensureNotificationAboutNewApplicationIsSentToBossesAndDepartmentHeads() throws MessagingException,
        IOException {

        ApplicationComment comment = createDummyComment(person, "Hätte gerne Urlaub");

        mailService.sendNewApplicationNotification(application, comment);

        // was email sent to boss?
        List<Message> inboxOfBoss = Mailbox.get(boss.getEmail());
        assertTrue("Boss should get the email", inboxOfBoss.size() > 0);

        // was email sent to department head?
        List<Message> inboxOfDepartmentHead = Mailbox.get(departmentHead.getEmail());
        assertTrue("Department head should get the email", inboxOfDepartmentHead.size() > 0);

        // get email
        Message msg = inboxOfDepartmentHead.get(0);

        // check subject
        assertEquals("Neuer Urlaubsantrag", msg.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(boss.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Chefs"));
        assertTrue(content.contains("Lieschen Müller"));
        assertTrue(content.contains("es liegt ein neuer zu genehmigender Antrag vor"));
        assertTrue("No comment in mail content", content.contains(comment.getText()));
        assertTrue("Wrong comment author", content.contains(comment.getPerson().getNiceName()));
    }


    private ApplicationComment createDummyComment(Person author, String text) {

        ApplicationComment comment = new ApplicationComment(author);
        comment.setText(text);

        return comment;
    }


    @Test
    public void ensureNotificationAboutNewApplicationContainsInformationAboutDepartmentVacations()
        throws MessagingException, IOException {

        Person departmentMember = TestDataCreator.createPerson("muster", "Marlene", "Muster", "mmuster@foo.de");
        Application departmentApplication = TestDataCreator.createApplication(departmentMember,
                TestDataCreator.getVacationType(VacationType.HOLIDAY), new DateMidnight(2015, 11, 5),
                new DateMidnight(2015, 11, 6), DayLength.FULL);

        Person otherDepartmentMember = TestDataCreator.createPerson("schmidt", "Niko", "Schmidt", "nschmidt@foo.de");
        Application otherDepartmentApplication = TestDataCreator.createApplication(otherDepartmentMember,
                TestDataCreator.getVacationType(VacationType.HOLIDAY), new DateMidnight(2015, 11, 4),
                new DateMidnight(2015, 11, 4), DayLength.MORNING);

        Mockito.when(personService.getPersonsWithNotificationType(MailNotification.NOTIFICATION_BOSS))
            .thenReturn(Collections.singletonList(boss));

        Mockito.when(departmentService.getApplicationsForLeaveOfMembersInDepartmentsOfPerson(Mockito.eq(person),
                    Mockito.any(DateMidnight.class), Mockito.any(DateMidnight.class)))
            .thenReturn(Arrays.asList(departmentApplication, otherDepartmentApplication));

        mailService.sendNewApplicationNotification(application, null);

        List<Message> inboxOfBoss = Mailbox.get(boss.getEmail());
        Message message = inboxOfBoss.get(0);

        String content = (String) message.getContent();

        assertTrue(content.contains("Marlene Muster: 05.11.2015 bis 06.11.2015"));
        assertTrue(content.contains("Niko Schmidt: 04.11.2015 bis 04.11.2015"));
    }


    @Test
    public void ensureNotificationAboutAllowedApplicationIsSentToOfficeAndThePerson() throws MessagingException,
        IOException {

        application.setBoss(boss);

        ApplicationComment comment = createDummyComment(boss, "OK, Urlaub kann genommen werden");

        mailService.sendAllowedNotification(application, comment);

        // were both emails sent?
        List<Message> inboxOffice = Mailbox.get(office.getEmail());
        assertTrue(inboxOffice.size() > 0);

        List<Message> inboxUser = Mailbox.get(person.getEmail());
        assertTrue(inboxUser.size() > 0);

        // get email user
        Message msg = inboxUser.get(0);

        // check subject
        assertEquals("Dein Urlaubsantrag wurde bewilligt", msg.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of user email
        String contentUser = (String) msg.getContent();
        assertTrue(contentUser.contains("Lieschen Müller"));
        assertTrue(contentUser.contains("gestellter Antrag wurde von Hugo Boss genehmigt"));
        assertTrue("No comment in mail content", contentUser.contains(comment.getText()));
        assertTrue("Wrong comment author", contentUser.contains(comment.getPerson().getNiceName()));

        // get email office
        Message msgOffice = inboxOffice.get(0);

        // check subject
        assertEquals("Neuer bewilligter Antrag", msgOffice.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(office.getEmail()), msgOffice.getAllRecipients()[0]);

        // check content of office email
        String contentOfficeMail = (String) msgOffice.getContent();
        assertTrue(contentOfficeMail.contains("Hallo Office"));
        assertTrue(contentOfficeMail.contains("es liegt ein neuer genehmigter Antrag vor"));
        assertTrue(contentOfficeMail.contains("Lieschen Müller"));
        assertTrue(contentOfficeMail.contains("Erholungsurlaub"));
        assertTrue("No comment in mail content", contentOfficeMail.contains(comment.getText()));
        assertTrue("Wrong comment author", contentOfficeMail.contains(comment.getPerson().getNiceName()));
    }


    @Test
    public void ensureNotificationAboutTemporaryAllowedApplicationIsSentToSecondStageAuthoritiesAndToPerson()
        throws MessagingException, IOException {

        ApplicationComment comment = createDummyComment(secondStage, "OK, spricht von meiner Seite aus nix dagegen");

        mailService.sendTemporaryAllowedNotification(application, comment);

        // were both emails sent?
        List<Message> inboxSecondStage = Mailbox.get(secondStage.getEmail());
        assertTrue(inboxSecondStage.size() > 0);

        List<Message> inboxUser = Mailbox.get(person.getEmail());
        assertTrue(inboxUser.size() > 0);

        // get email user
        Message msg = inboxUser.get(0);

        // check subject
        assertEquals("Dein Urlaubsantrag wurde vorläufig bewilligt", msg.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of user email
        String contentUser = (String) msg.getContent();
        assertTrue(contentUser.contains("Hallo Lieschen Müller"));
        assertTrue(contentUser.contains(
                "Bitte beachte, dass dieser erst noch von einem entsprechend Verantwortlichen freigegeben werden muss"));
        assertTrue("No comment in mail content", contentUser.contains(comment.getText()));
        assertTrue("Wrong comment author", contentUser.contains(comment.getPerson().getNiceName()));

        // get email office
        Message msgSecondStage = inboxSecondStage.get(0);

        // check subject
        assertEquals("Ein Urlaubsantrag wurde vorläufig bewilligt", msgSecondStage.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(secondStage.getEmail()), msgSecondStage.getAllRecipients()[0]);

        // check content of office email
        String contentSecondStageMail = (String) msgSecondStage.getContent();
        assertTrue(contentSecondStageMail.contains(
                "Der Antrag wurde bereits vorläufig genehmigt und muss nun noch endgültig freigegeben werden"));
        assertTrue(contentSecondStageMail.contains("Lieschen Müller"));
        assertTrue(contentSecondStageMail.contains("Erholungsurlaub"));
        assertTrue("No comment in mail content", contentSecondStageMail.contains(comment.getText()));
        assertTrue("Wrong comment author", contentSecondStageMail.contains(comment.getPerson().getNiceName()));
    }


    @Test
    public void ensureNotificationAboutRejectedApplicationIsSentToPerson() throws MessagingException, IOException {

        ApplicationComment comment = createDummyComment(boss, "Geht leider nicht zu dem Zeitraum");

        mailService.sendRejectedNotification(application, comment);

        // was email sent?
        List<Message> inbox = Mailbox.get(person.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertEquals("Dein Urlaubsantrag wurde abgelehnt", msg.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Lieschen Müller"));
        assertTrue(content.contains("wurde leider von Hugo Boss abgelehnt"));
        assertTrue("No comment in mail content", content.contains(comment.getText()));
        assertTrue("Wrong comment author", content.contains(comment.getPerson().getNiceName()));
    }


    @Test
    public void ensureAfterApplyingForLeaveAConfirmationNotificationIsSentToPerson() throws MessagingException,
        IOException {

        ApplicationComment comment = createDummyComment(person, "Hätte gerne Urlaub");

        mailService.sendConfirmation(application, comment);

        // was email sent?
        List<Message> inbox = Mailbox.get(person.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Antragsstellung"));

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Lieschen Müller"));
        assertTrue(content.contains("dein Urlaubsantrag wurde erfolgreich eingereicht"));
        assertTrue("No comment in mail content", content.contains(comment.getText()));
        assertTrue("Wrong comment author", content.contains(comment.getPerson().getNiceName()));
    }


    @Test
    public void ensurePersonGetsANotificationIfOfficeCancelledOneOfHisApplications() throws MessagingException,
        IOException {

        application.setCanceller(office);

        ApplicationComment comment = createDummyComment(office, "Geht leider nicht");

        mailService.sendCancelledByOfficeNotification(application, comment);

        // was email sent?
        List<Message> inboxApplicant = Mailbox.get(person.getEmail());
        assertTrue(inboxApplicant.size() > 0);

        Message msg = inboxApplicant.get(0);

        // check subject
        assertEquals("Dein Antrag wurde storniert", msg.getSubject());

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Lieschen Müller"));
        assertTrue(content.contains("dein Urlaubsantrag wurde von Marlene Muster für dich storniert"));
        assertTrue("No comment in mail content", content.contains(comment.getText()));
        assertTrue("Wrong comment author", content.contains(comment.getPerson().getNiceName()));
    }


    @Test
    public void ensureAdministratorGetsANotificationIfASignErrorOccurred() throws MessagingException, IOException {

        mailService.sendSignErrorNotification(5, "Message of exception");

        List<Message> inbox = Mailbox.get(settings.getMailSettings().getAdministrator());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        assertEquals("Fehler beim Signieren eines Antrags", msg.getSubject());

        String content = (String) msg.getContent();

        assertTrue(content.contains(
                "Beim Versuch den Urlaubsantrag mit der ID '5' zu signieren, ist ein Fehler aufgetreten."));
        assertTrue(content.contains("Message of exception"));
    }


    @Test
    public void ensurePersonGetsANotificationIfAnOfficeMemberAppliedForLeaveForThisPerson() throws MessagingException,
        IOException {

        ApplicationComment comment = createDummyComment(office, "Habe das mal für dich beantragt");

        application.setApplier(office);
        mailService.sendAppliedForLeaveByOfficeNotification(application, comment);

        // was email sent?
        List<Message> inbox = Mailbox.get(person.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Für dich wurde ein Urlaubsantrag eingereicht"));

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Lieschen Müller"));
        assertTrue(content.contains("Marlene Muster hat einen Urlaubsantrag für dich gestellt"));
        assertTrue("No comment in mail content", content.contains(comment.getText()));
        assertTrue("Wrong comment author", content.contains(comment.getPerson().getNiceName()));
    }


    @Test
    public void ensureCorrectFrom() throws MessagingException, IOException {

        mailService.sendConfirmation(application, null);

        List<Message> inbox = Mailbox.get(person.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        Address[] from = msg.getFrom();
        Assert.assertNotNull("From must be set", from);
        Assert.assertEquals("From must be only one email address", 1, from.length);
        Assert.assertEquals("Wrong from", settings.getMailSettings().getFrom(), from[0].toString());
    }


    @Test
    public void ensureOfficeGetsNotificationAfterAccountUpdating() throws MessagingException, IOException {

        Account accountOne = new Account();
        accountOne.setRemainingVacationDays(new BigDecimal("3"));
        accountOne.setPerson(TestDataCreator.createPerson("muster", "Marlene", "Muster", "marlene@muster.de"));

        Account accountTwo = new Account();
        accountTwo.setRemainingVacationDays(new BigDecimal("5.5"));
        accountTwo.setPerson(TestDataCreator.createPerson("mustermann", "Max", "Mustermann", "max@mustermann.de"));

        Account accountThree = new Account();
        accountThree.setRemainingVacationDays(new BigDecimal("-1"));
        accountThree.setPerson(TestDataCreator.createPerson("dings", "Horst", "Dings", "horst@dings.de"));

        mailService.sendSuccessfullyUpdatedAccountsNotification(Arrays.asList(accountOne, accountTwo, accountThree));

        // ENSURE OFFICE MEMBERS HAVE GOT CORRECT EMAIL
        List<Message> inboxOffice = Mailbox.get(office.getEmail());
        assertTrue(inboxOffice.size() > 0);

        Message mail = inboxOffice.get(0);

        // check subject
        assertEquals("Wrong subject", "Auswertung Resturlaubstage", mail.getSubject());

        // check content
        String content = (String) mail.getContent();
        assertTrue(content.contains("Stand Resturlaubstage zum 1. Januar " + DateMidnight
                .now().getYear()));
        assertTrue(content.contains("Marlene Muster: 3"));
        assertTrue(content.contains("Max Mustermann: 5"));
        assertTrue(content.contains("Horst Dings: -1"));
    }


    @Test
    public void ensureCorrectHolidayReplacementMailIsSent() throws MessagingException, IOException {

        Person holidayReplacement = TestDataCreator.createPerson("replacement", "Mar", "Teria",
                "replacement@muster.de");
        application.setHolidayReplacement(holidayReplacement);

        mailService.notifyHolidayReplacement(application);

        // was email sent?
        List<Message> inbox = Mailbox.get(holidayReplacement.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Urlaubsvertretung"));

        // check from and recipient
        assertEquals(new InternetAddress(holidayReplacement.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Mar Teria"));
        assertTrue(content.contains("Urlaubsvertretung"));
    }


    @Test
    public void ensureAdministratorGetsANotificationIfACalendarSyncErrorOccurred() throws MessagingException,
        IOException {

        Absence absence = Mockito.mock(Absence.class);
        Mockito.when(absence.getPerson()).thenReturn(person);
        Mockito.when(absence.getStartDate()).thenReturn(DateMidnight.now().toDate());
        Mockito.when(absence.getEndDate()).thenReturn(DateMidnight.now().toDate());

        mailService.sendCalendarSyncErrorNotification("Kalendername", absence, "Calendar sync failed");

        List<Message> inbox = Mailbox.get(settings.getMailSettings().getAdministrator());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        assertEquals("Fehler beim Synchronisieren des Kalenders", msg.getSubject());

        String content = (String) msg.getContent();
        assertTrue(content.contains("Kalendername"));
        assertTrue(content.contains("Calendar sync failed"));
        assertTrue(content.contains(person.getNiceName()));
    }


    @Test
    public void ensureAdministratorGetsANotificationIfAnErrorOccurredDuringEventDeletion() throws MessagingException,
        IOException {

        mailService.sendCalendarDeleteErrorNotification("Kalendername", "ID-123456", "event delete failed");

        List<Message> inbox = Mailbox.get(settings.getMailSettings().getAdministrator());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        assertEquals("Fehler beim Löschen eines Kalendereintrags", msg.getSubject());

        String content = (String) msg.getContent();
        assertTrue(content.contains("Kalendername"));
        assertTrue(content.contains("ID-123456"));
        assertTrue(content.contains("event delete failed"));
    }


    @Test
    public void ensureAdministratorGetsANotificationIfAEventUpdateErrorOccurred() throws MessagingException,
        IOException {

        Absence absence = Mockito.mock(Absence.class);
        Mockito.when(absence.getPerson()).thenReturn(person);
        Mockito.when(absence.getStartDate()).thenReturn(DateMidnight.now().toDate());
        Mockito.when(absence.getEndDate()).thenReturn(DateMidnight.now().toDate());

        mailService.sendCalendarUpdateErrorNotification("Kalendername", absence, "ID-123456", "event update failed");

        List<Message> inbox = Mailbox.get(settings.getMailSettings().getAdministrator());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        assertEquals("Fehler beim Aktualisieren eines Kalendereintrags", msg.getSubject());

        String content = (String) msg.getContent();
        assertTrue(content.contains("Kalendername"));
        assertTrue(content.contains("ID-123456"));
        assertTrue(content.contains("event update failed"));
        assertTrue(content.contains(person.getNiceName()));
    }


    @Test
    public void ensureAdministratorGetsANotificationIfSettingsGetUpdated() throws MessagingException, IOException {

        mailService.sendSuccessfullyUpdatedSettingsNotification(settings);

        List<Message> inbox = Mailbox.get(settings.getMailSettings().getAdministrator());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        assertEquals("Einstellungen aktualisiert", msg.getSubject());

        String content = (String) msg.getContent();
        assertTrue(content.contains("Einstellungen"));
        assertTrue(content.contains(settings.getMailSettings().getHost()));
        assertTrue(content.contains(settings.getMailSettings().getPort().toString()));
    }


    @Test
    public void ensureThatSendUserCreationNotification() throws MessagingException, IOException {

        Person user = TestDataCreator.createPerson("neuer", "Manuel", "Neuer", "neuer@test.de");
        String rawPassword = "secret";

        mailService.sendUserCreationNotification(user, rawPassword);

        List<Message> inbox = Mailbox.get(user.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Account für Urlaubsverwaltung erstellt"));

        // check from and recipient
        assertEquals(new InternetAddress(user.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Manuel Neuer"));
        assertTrue(content.contains(
                "Du hast soeben einen Benutzeraccount für die Urlaubsverwaltung angelegt bekommen"));
        assertTrue(content.contains(user.getLoginName()));
        assertTrue(content.contains(rawPassword));
    }


    @Test
    public void ensureOfficeGetsMailAboutCancellationRequest() throws MessagingException, IOException {

        ApplicationComment comment = createDummyComment(person, "Bitte stornieren!");

        mailService.sendCancellationRequest(application, comment);

        List<Message> inbox = Mailbox.get(office.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Ein Benutzer beantragt die Stornierung eines genehmigten Antrags"));

        // check from and recipient
        assertEquals(new InternetAddress(office.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Office"));
        assertTrue(content.contains("hat beantragt den bereits genehmigten Urlaub"));
    }


    @Test
    public void ensureCorrectReferMail() throws MessagingException, IOException {

        Person recipient = TestDataCreator.createPerson("recipient", "Max", "Muster", "mustermann@test.de");
        Person sender = TestDataCreator.createPerson("sender", "Rick", "Grimes", "rick@grimes.com");

        mailService.sendReferApplicationNotification(application, recipient, sender);

        List<Message> inbox = Mailbox.get(recipient.getEmail());
        assertTrue(inbox.size() > 0);

        Message msg = inbox.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Hilfe bei der Entscheidung über einen Urlaubsantrag"));

        // check from and recipient
        assertEquals(new InternetAddress(recipient.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Max Muster"));
        assertTrue(content.contains("Rick Grimes bittet dich um Hilfe bei der Entscheidung über einen Urlaubsantrag"));
    }


    @Test
    public void ensureBossesAndDepartmentHeadsGetRemindMail() throws MessagingException, IOException {

        mailService.sendRemindBossNotification(application);

        // was email sent to boss?
        List<Message> inboxOfBoss = Mailbox.get(boss.getEmail());
        assertTrue("Boss should get the email", inboxOfBoss.size() > 0);

        // was email sent to department head?
        List<Message> inboxOfDepartmentHead = Mailbox.get(departmentHead.getEmail());
        assertTrue("Department head should get the email", inboxOfDepartmentHead.size() > 0);

        // has mail correct attributes?
        Message msg = inboxOfBoss.get(0);

        // check subject
        assertTrue(msg.getSubject().contains("Erinnerung wartender Urlaubsantrag"));

        // check from and recipient
        assertEquals(new InternetAddress(boss.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo liebe Chefs"));
    }


    @Test
    public void ensurePersonGetsMailIfApplicationForLeaveHasBeenConvertedToSickNote() throws MessagingException,
        IOException {

        application.setApplier(office);

        mailService.sendSickNoteConvertedToVacationNotification(application);

        // was email sent?
        List<Message> inbox = Mailbox.get(person.getEmail());
        assertTrue("Person should get email", inbox.size() > 0);

        // has mail correct attributes?
        Message msg = inbox.get(0);

        // check subject
        assertTrue("Wrong subject", msg.getSubject().contains("Deine Krankmeldung wurde zu Urlaub umgewandelt"));

        // check from and recipient
        assertEquals(new InternetAddress(person.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Lieschen Müller"));
        assertTrue(content.contains("Marlene Muster hat deine Krankmeldung zu Urlaub umgewandelt"));
    }


    @Test
    public void ensurePersonAndOfficeGetMailIfSickNoteReachesEndOfSickPay() throws MessagingException, IOException {

        SickNote sickNote = TestDataCreator.createSickNote(person);

        mailService.sendEndOfSickPayNotification(sickNote);

        // was email sent to office?
        List<Message> inboxOffice = Mailbox.get(office.getEmail());
        assertTrue("Person should get email", inboxOffice.size() > 0);

        // was email sent to person?
        List<Message> inboxPerson = Mailbox.get(person.getEmail());
        assertTrue("Person should get email", inboxPerson.size() > 0);

        // has mail correct attributes?
        assertCorrectEndOfSickPayMail(inboxOffice.get(0), office);
        assertCorrectEndOfSickPayMail(inboxPerson.get(0), person);
    }


    private void assertCorrectEndOfSickPayMail(Message msg, Person recipient) throws MessagingException, IOException {

        // check subject
        assertTrue("Wrong subject", msg.getSubject().contains("Ende der Lohnfortzahlung"));

        // check from and recipient
        assertEquals(new InternetAddress(recipient.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Lieschen Müller, hallo Office"));
        assertTrue(content.contains(
                "Der Anspruch auf Lohnfortzahlung durch den Arbeitgeber im Krankheitsfall besteht für maximal sechs Wochen"));
    }


    @Test
    public void ensureOfficeWithOvertimeNotificationGetMailIfOvertimeRecorded() throws MessagingException, IOException {

        Overtime overtimeRecord = TestDataCreator.createOvertimeRecord(person);
        OvertimeComment overtimeComment = new OvertimeComment(person, overtimeRecord, OvertimeAction.CREATED);

        Mockito.when(personService.getPersonsWithNotificationType(MailNotification.OVERTIME_NOTIFICATION_OFFICE))
            .thenReturn(Collections.singletonList(office));

        mailService.sendOvertimeNotification(overtimeRecord, overtimeComment);

        // was email sent to office?
        List<Message> inboxOffice = Mailbox.get(office.getEmail());
        assertTrue("Person should get email", inboxOffice.size() > 0);

        // has mail correct attributes?
        Message msg = inboxOffice.get(0);

        // check subject
        assertTrue("Wrong subject", msg.getSubject().contains("Es wurden Überstunden eingetragen"));

        // check from and recipient
        assertEquals(new InternetAddress(office.getEmail()), msg.getAllRecipients()[0]);

        // check content of email
        String content = (String) msg.getContent();
        assertTrue(content.contains("Hallo Office"));
        assertTrue(content.contains("es wurden Überstunden erfasst"));
    }
}
