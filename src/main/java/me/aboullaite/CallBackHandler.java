package me.aboullaite;

import com.github.messenger4j.MessengerPlatform;
import com.github.messenger4j.exceptions.MessengerApiException;
import com.github.messenger4j.exceptions.MessengerIOException;
import com.github.messenger4j.exceptions.MessengerVerificationException;
import com.github.messenger4j.receive.MessengerReceiveClient;
import com.github.messenger4j.receive.events.AccountLinkingEvent;
import com.github.messenger4j.receive.handlers.*;
import com.github.messenger4j.send.*;
import com.github.messenger4j.send.buttons.Button;
import com.github.messenger4j.send.templates.GenericTemplate;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.FacebookClient.AccessToken;
import com.restfb.Parameter;
import com.restfb.types.User;

import me.aboullaite.data.UserRespository;
import me.aboullaite.domain.SearchResult;
import me.aboullaite.entity.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Created by aboullaite on 2017-02-26.
 */

@RestController
@RequestMapping("/callback")
public class CallBackHandler {

    private static final Logger logger = LoggerFactory.getLogger(CallBackHandler.class);

    private static final String RESOURCE_URL =
            "https://raw.githubusercontent.com/fbsamples/messenger-platform-samples/master/node/public";
    public static final String GOOD_ACTION = "DEVELOPER_DEFINED_PAYLOAD_FOR_GOOD_ACTION";
    public static final String NOT_GOOD_ACTION = "DEVELOPER_DEFINED_PAYLOAD_FOR_NOT_GOOD_ACTION";
    
    public static final String YES = "PROCEED";
    public static final String NO = "DO_NOT_PROCEED";
    
    public static final String ONE = "ONE_TO_THREE_CUPS";
    public static final String TWO = "THREE_TO_FIVE_CUPS";
    public static final String THREE = "MORE_THAN_THREE_CUPS";
    
    public static final String ONCE = "REMIND_ONCE_PER_DAY";
    public static final String TWICE = "REMIND_TWICE_PER_DAY";
    public static final String THREE_TIMES = "REMIND_THREE_TIMES";
    
    public static final String DONE = "DONE_DRINKING_ONE_CUP";
    
    public static final String ABOUT = "ABOUT_WATERBOT";
    public static final String CHANGE = "CHANGE_FREQUENCY_ALERTS";
    
    public static final String BACK = "BACK_TO_MENU";
    
    public static final String ONCE_NEW = "REMIND_ONCE_PER_DAY_NEW";
    public static final String TWICE_NEW = "REMIND_TWICE_PER_DAY_NEW";
    public static final String THREE_TIMES_NEW = "REMIND_THREE_TIMES_NEW";
    public static final String STOP = "STOP_REMINDING";
    
    public static final String EXIT = "STOP_CONVERSATION";
    private final MessengerReceiveClient receiveClient;
    private final MessengerSendClient sendClient;
    
    private String accessToken =  "EAAB7JHEiUwoBAAgRh8kkDcGKUpGDuuM6Uio6MixCQCTjojPqK31cl1k9QgFUJsnJntXJH1GX5QjEuMRSiD3xMyUIi1hLKdGoujeQiDPPUfJbn01cI4cZCAKXfXy0bwtHPUZCZBOOYXlolsMQYa57YMY6wffm3qtHhAZAn6ZCnZAQZDZD";
    
    @Autowired
	private UserRespository userRespository;
    
    private FbUser user = new FbUser();

    /**
     * Constructs the {@code CallBackHandler} and initializes the {@code MessengerReceiveClient}.
     */
    @Autowired
    public CallBackHandler(@Value("${messenger4j.appSecret}") final String appSecret,
                                            @Value("${messenger4j.verifyToken}") final String verifyToken,
                                            final MessengerSendClient sendClient) {

        logger.debug("Initializing MessengerReceiveClient - appSecret: {} | verifyToken: {}", appSecret, verifyToken);
        this.receiveClient = MessengerPlatform.newReceiveClientBuilder(appSecret, verifyToken)
                .onQuickReplyMessageEvent(newQuickReplyMessageEventHandler())
                .onTextMessageEvent(newTextMessageEventHandler())
                .onPostbackEvent(newPostbackEventHandler())
                .onAccountLinkingEvent(newAccountLinkingEventHandler())
                .onOptInEvent(newOptInEventHandler())
                .onEchoMessageEvent(newEchoMessageEventHandler())
                .onMessageDeliveredEvent(newMessageDeliveredEventHandler())
                .onMessageReadEvent(newMessageReadEventHandler())
                .fallbackEventHandler(newFallbackEventHandler())
                .build();
        this.sendClient = sendClient;
    }

    /**
     * Webhook verification endpoint.
     */
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> verifyWebhook(@RequestParam("hub.mode") final String mode,
                                                @RequestParam("hub.verify_token") final String verifyToken,
                                                @RequestParam("hub.challenge") final String challenge) {

        logger.debug("Received Webhook verification request - mode: {} | verifyToken: {} | challenge: {}", mode,
                verifyToken, challenge);
        try {
            return ResponseEntity.ok(this.receiveClient.verifyWebhook(mode, verifyToken, challenge));
        } catch (MessengerVerificationException e) {
            logger.warn("Webhook verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * Callback endpoint responsible for processing the inbound messages and events.
     */
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<Void> handleCallback(@RequestBody final String payload,
                                               @RequestHeader("X-Hub-Signature") final String signature) {

        logger.debug("Received Messenger Platform callback - payload: {} | signature: {}", payload, signature);
        try {
            this.receiveClient.processCallbackPayload(payload, signature);
            logger.debug("Processed callback payload successfully");
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (MessengerVerificationException e) {
            logger.warn("Processing of callback payload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    //reminder (time zone creates a problem - 10AM is for cron 9)
    @Scheduled(cron = "0 0 9,12,14,19 * * *")
    public void sendAlert() throws MessengerApiException, MessengerIOException {
    	
        long seconds = System.currentTimeMillis() / 1000;
        
        //long s = seconds % 60;
        //long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        //hour fix(timezone)
        h+=1;
        
        //String currTime = String.format("%d:%02d:%02d", h,m,s);
        List<FbUser> users = userRespository.findAll();
        
        for(FbUser user : users) {
	        if(user.getReminder()==1 && h==13) {
	        	sendTextMessage(user.getUserId(), "Water time :D");
	        	sendGifMessage(user.getUserId(), "https://scontent.fbeg4-1.fna.fbcdn.net/v/t34.0-0/p280x280/16977100_258538244589024_759638287_n.gif?fallback=1&oh=2275988307557ffdec37bd52d68ceb2d&oe=59F678BE");
	        }
	        else if(user.getReminder()==2 && (h==10 || h==20)) {
	        	if(h==10)
	        		sendTextMessage(user.getUserId(), "Good morning champ! Time for morning drink :)");
	        	else
	        		sendTextMessage(user.getUserId(), "Water time! :D");
	        	sendGifMessage(user.getUserId(), "https://scontent.fbeg4-1.fna.fbcdn.net/v/t34.0-0/p280x280/16977100_258538244589024_759638287_n.gif?fallback=1&oh=2275988307557ffdec37bd52d68ceb2d&oe=59F678BE");
	        }
	        else if(user.getReminder()==3 && (h==10 || h==15 || h==20)){
	        	if(h==10)
	        		sendTextMessage(user.getUserId(), "Good morning champ! Time for morning drink :)");
	        	else if(h==15)
	        		sendTextMessage(user.getUserId(), "Time for your drink :D");
	        	else
	        		sendTextMessage(user.getUserId(), "Water time! :D");
	        	
	        	sendGifMessage(user.getUserId(), "https://scontent.fbeg4-1.fna.fbcdn.net/v/t34.0-0/p280x280/16977100_258538244589024_759638287_n.gif?fallback=1&oh=2275988307557ffdec37bd52d68ceb2d&oe=59F678BE");
	        }
        }
    }
    
    //method for getting user name from user id (RESTFB)
    public static String getUserName(String accessToken, String senderId) {
        FacebookClient facebookClient = new DefaultFacebookClient(accessToken);
        User user = facebookClient.fetchObject(senderId, User.class);
        return user.getFirstName();
    }
    
    private TextMessageEventHandler newTextMessageEventHandler() {
        return event -> {
            logger.debug("Received TextMessageEvent: {}", event);

            final String messageId = event.getMid();
            final String messageText = event.getText();
            final String senderId = event.getSender().getId();
            final Date timestamp = event.getTimestamp();
            final String recipientId = event.getRecipient().getId();
           

            logger.info("Received message '{}' with text '{}' from user '{}' at '{}'",
                    messageId, messageText, senderId, timestamp);
           
            try {
                switch (messageText.toLowerCase()) {


                    case "yo":
                    	user.setUserId(senderId);
                    	user.setReminder(0);
                    	sendTextMessage(senderId, "Hey " + getUserName(accessToken, senderId) + "!");
                        sendQuickReply2(senderId);
                        break;
                    
                    case "menu":
                    	user.setUserId(senderId);
                    	user.setReminder(0);
                   	 	sendQuickReply6(senderId);
                        break;
                        
                    default:
                        sendReadReceipt(senderId);
                        sendTypingOn(senderId);
                        sendTextMessage(senderId, "Sorry i am young chatbot, i do not understand that :/ Type \"Yo\" to start.");
                        sendTypingOff(senderId);
                }
            } catch (MessengerApiException | MessengerIOException e) {
                handleSendException(e);
            }
        };
    }

    private void sendGifMessage(String recipientId, String gif) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendImageAttachment(recipientId, gif);
    }
    
    //Quick Reply BEGIN
    private void sendQuickReply2(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Yes", YES).toList()
                .addTextQuickReply("No", NO).toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "I am your personal water trainer :) Do you want to proceed?", quickReplies);
    }
    
    private void sendQuickReply3(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("1-3", ONE).toList()
                .addTextQuickReply("3-5", TWO).toList()
                .addTextQuickReply("5+", THREE).toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "How many glases of water you drink per day? :)", quickReplies);
    }
    
    private void sendQuickReply4(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Once a day", ONCE).toList()
                .addTextQuickReply("Twice a day", TWICE).toList()
                .addTextQuickReply("3 times a day", THREE_TIMES).toList()
                .build();
        
        		

        this.sendClient.sendTextMessage(recipientId, "Choose the frequency for water break reminders", quickReplies);
    }
    
    private void sendQuickReply5(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Done", DONE).toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "Drink 1 cup of water and press \"Done\" when you finish", quickReplies);
    }
    
    private void sendQuickReply6(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("About Bot", ABOUT).toList()
                .addTextQuickReply("Change Alerts", CHANGE).toList()
                .addTextQuickReply("Exit", EXIT).toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "This is your menu. You can reach it by writting \"Menu\".", quickReplies);
    }
    
    private void sendQuickReply7(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Back", BACK).toList()
                .addTextQuickReply("Exit", EXIT).toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "Smart Water Bot's goal is to help you drink more water for healthier life 💪", quickReplies);
    }
    
    private void sendQuickReply8(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Once a day", ONCE_NEW).toList()
                .addTextQuickReply("Twice a day", TWICE_NEW).toList()
                .addTextQuickReply("3 times a day", THREE_TIMES_NEW).toList()
                .addTextQuickReply("Do not remind me", STOP).toList()
                .build();
        
        this.sendClient.sendTextMessage(recipientId, "Changing frequency is super easy. Select new frequency", quickReplies);
    }
    
    private void sendQuickReplyStop(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Menu", BACK).toList()
                .addTextQuickReply("Exit", EXIT).toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "Your new frequency was set :) thanks :D", quickReplies);
    }
    //Quick Reply END
    
    private void sendReadReceipt(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendSenderAction(recipientId, SenderAction.MARK_SEEN);
    }

    private void sendTypingOn(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendSenderAction(recipientId, SenderAction.TYPING_ON);
    }

    private void sendTypingOff(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendSenderAction(recipientId, SenderAction.TYPING_OFF);
    }

    private QuickReplyMessageEventHandler newQuickReplyMessageEventHandler() {
        return event -> {
            logger.debug("Received QuickReplyMessageEvent: {}", event);

            final String senderId = event.getSender().getId();
            final String messageId = event.getMid();
            final String quickReplyPayload = event.getQuickReply().getPayload();

            logger.info("Received quick reply for message '{}' with payload '{}'", messageId, quickReplyPayload);


                try {
                    if(quickReplyPayload.equals(GOOD_ACTION))
                    sendGifMessage(senderId, "https://media.giphy.com/media/3oz8xPxTUeebQ8pL1e/giphy.gif");
                    else if(quickReplyPayload.equals(YES)) {
                    	sendGifMessage(senderId, "https://media.giphy.com/media/3oz8xPxTUeebQ8pL1e/giphy.gif");
                    	sendTextMessage(senderId, "Great!");
                    	sendTypingOn(senderId);
                    	sendTextMessage(senderId, "Before we begin...");
                    	sendTypingOff(senderId);
                    	sendQuickReply3(senderId);
                    }
                    else if(quickReplyPayload.equals(NO)) {
                    	sendGifMessage(senderId, "https://media.giphy.com/media/d2lcHJTG5Tscg/giphy.gif");
                    	sendTextMessage(senderId, "Ok, i understand. Come back soon :) You can reach me by typing \"Yo\"");
                    }
                    else if(quickReplyPayload.equals(ONE)) {
                    	//sending picture
                    	sendGifMessage(senderId, "https://media.giphy.com/media/d2lcHJTG5Tscg/giphy.gif");
                    	//this.sendClient.sendImageAttachment(senderId, "https://scontent.fbeg4-1.fna.fbcdn.net/v/t34.0-0/p280x280/17821593_277151852727663_561650605_n.jpg?oh=3235fe5fa6e06abd8b8b071398d443e9&oe=59F50916");
                    	sendTypingOn(senderId);
                    	sendInfoTextMessage(senderId);
                    	sendTypingOff(senderId);
                    	sendQuickReply4(senderId);
                    }
                    else if(quickReplyPayload.equals(TWO)) {
                    	//sending picture
                    	this.sendClient.sendImageAttachment(senderId, "https://betanews.com/wp-content/uploads/2014/01/Happy-man.jpg");
                    	sendTypingOn(senderId);
                    	sendInfoTextMessage(senderId);
                    	sendTypingOff(senderId);
                    	sendQuickReply4(senderId);
                    }
                    else if(quickReplyPayload.equals(THREE)) {
                    	//sending picture
                    	this.sendClient.sendImageAttachment(senderId, "https://girlishblunders.files.wordpress.com/2012/12/happy-old-man.jpg");
                    	sendTypingOn(senderId);
                    	sendInfoTextMessage(senderId);
                    	sendTypingOff(senderId);
                    	sendQuickReply4(senderId);
                    }
                    else if(quickReplyPayload.equals(ONCE)) {
                    	sendTextMessage(senderId, "Noted :) Let's give it a try now!");
                    	user.setReminder(1);
                    	userRespository.save(user);
                    	sendQuickReply5(senderId);
                    }
                    else if(quickReplyPayload.equals(TWICE)) {
                    	sendTextMessage(senderId, "Noted :) Let's give it a try now!");
                    	user.setReminder(2);
                    	userRespository.save(user);
                    	sendQuickReply5(senderId);
                    }
                    else if(quickReplyPayload.equals(THREE_TIMES)) {
                    	sendTextMessage(senderId, "Noted :) Let's give it a try now!");
                    	user.setReminder(3);
                    	userRespository.save(user);
                    	sendQuickReply5(senderId);
                    }
                    else if(quickReplyPayload.equals(STOP)) {
                    	user.setReminder(0);
                    	userRespository.save(user);
                    	sendQuickReplyStop(senderId);
                    }
                    else if(quickReplyPayload.equals(DONE)) {
                    	this.sendClient.sendImageAttachment(senderId, "https://quotlr.com/images/authors/11708-usain_bolt.jpg");
                    	sendTextMessage(senderId, "Well done! Keep it up!");
                    	sendTypingOn(senderId);
                    	sendTextMessage(senderId, "You can always reach menu by typing \"Menu\".");
                    	sendTypingOff(senderId);
                    }
                    else if(quickReplyPayload.equals(ABOUT)) {
                    	sendReadReceipt(senderId);
                    	sendTextMessage(senderId, "Thanks for asking :)");
                    	sendTypingOn(senderId);
                    	sendTextMessage(senderId, "Smart Water Bot is created by Marko Petričić. Thank you for using it! :D");
                    	sendTypingOff(senderId);
                    	sendTypingOn(senderId);
                    	sendQuickReply7(senderId);
                    	sendTypingOff(senderId);
                    }
                    else if(quickReplyPayload.equals(BACK)) {
                    	sendQuickReply6(senderId);
                    }
                    else if(quickReplyPayload.equals(CHANGE)) {
                    	sendReadReceipt(senderId);
                    	sendQuickReply8(senderId);
                    }
                    else if(quickReplyPayload.equals(ONCE_NEW)) {
                    	user.setReminder(1);
                    	userRespository.save(user);
                    	sendReadReceipt(senderId);
                    	sendQuickReplyStop(senderId);
                    }
                    else if(quickReplyPayload.equals(TWICE_NEW)) {
                    	user.setReminder(2);
                    	userRespository.save(user);
                    	sendReadReceipt(senderId);
                    	sendQuickReplyStop(senderId);
                    }
                    else if(quickReplyPayload.equals(THREE_TIMES_NEW)) {
                    	user.setReminder(3);
                    	userRespository.save(user);
                    	sendReadReceipt(senderId);
                    	sendQuickReplyStop(senderId);
                    }
                    else if(quickReplyPayload.equals(EXIT)) {
                    	sendTypingOn(senderId);
                    	sendTextMessage(senderId, "See you soon :) You can reach me by typing \"Yo\" or \"Menu\" ");
                    	sendTypingOff(senderId);
                    }
                    else
                    sendGifMessage(senderId, "https://media.giphy.com/media/26ybx7nkZXtBkEYko/giphy.gif");
                } catch (MessengerApiException e) {
                    handleSendException(e);
                } catch (MessengerIOException e) {
                    handleIOException(e);
                }

        };
    }

    private PostbackEventHandler newPostbackEventHandler() {
        return event -> {
            logger.debug("Received PostbackEvent: {}", event);

            final String senderId = event.getSender().getId();
            final String recipientId = event.getRecipient().getId();
            final String payload = event.getPayload();
            final Date timestamp = event.getTimestamp();

            logger.info("Received postback for user '{}' and page '{}' with payload '{}' at '{}'",
                    senderId, recipientId, payload, timestamp);

            sendTextMessage(senderId, "Postback called");
        };
    }

    private AccountLinkingEventHandler newAccountLinkingEventHandler() {
        return event -> {
            logger.debug("Received AccountLinkingEvent: {}", event);

            final String senderId = event.getSender().getId();
            final AccountLinkingEvent.AccountLinkingStatus accountLinkingStatus = event.getStatus();
            final String authorizationCode = event.getAuthorizationCode();

            logger.info("Received account linking event for user '{}' with status '{}' and auth code '{}'",
                    senderId, accountLinkingStatus, authorizationCode);
        };
    }

    private OptInEventHandler newOptInEventHandler() {
        return event -> {
            logger.debug("Received OptInEvent: {}", event);

            final String senderId = event.getSender().getId();
            final String recipientId = event.getRecipient().getId();
            final String passThroughParam = event.getRef();
            final Date timestamp = event.getTimestamp();

            logger.info("Received authentication for user '{}' and page '{}' with pass through param '{}' at '{}'",
                    senderId, recipientId, passThroughParam, timestamp);

            sendTextMessage(senderId, "Authentication successful");
        };
    }

    private EchoMessageEventHandler newEchoMessageEventHandler() {
        return event -> {
            logger.debug("Received EchoMessageEvent: {}", event);

            final String messageId = event.getMid();
            final String recipientId = event.getRecipient().getId();
            final String senderId = event.getSender().getId();
            final Date timestamp = event.getTimestamp();

            logger.info("Received echo for message '{}' that has been sent to recipient '{}' by sender '{}' at '{}'",
                    messageId, recipientId, senderId, timestamp);
        };
    }

    private MessageDeliveredEventHandler newMessageDeliveredEventHandler() {
        return event -> {
            logger.debug("Received MessageDeliveredEvent: {}", event);

            final List<String> messageIds = event.getMids();
            final Date watermark = event.getWatermark();
            final String senderId = event.getSender().getId();

            if (messageIds != null) {
                messageIds.forEach(messageId -> {
                    logger.info("Received delivery confirmation for message '{}'", messageId);
                });
            }

            logger.info("All messages before '{}' were delivered to user '{}'", watermark, senderId);
        };
    }

    private MessageReadEventHandler newMessageReadEventHandler() {
        return event -> {
            logger.debug("Received MessageReadEvent: {}", event);

            final Date watermark = event.getWatermark();
            final String senderId = event.getSender().getId();

            logger.info("All messages before '{}' were read by user '{}'", watermark, senderId);
        };
    }

    /**
     * This handler is called when either the message is unsupported or when the event handler
     *  for the actual event type
     * is not registered. In this showcase all event handlers are registered. Hence only in case of an
     * unsupported message the fallback event handler is called.
     */
    private FallbackEventHandler newFallbackEventHandler() {
        return event -> {
            logger.debug("Received FallbackEvent: {}", event);

            final String senderId = event.getSender().getId();
            logger.info("Received unsupported message from user '{}'", senderId);
        };
    }

    private void sendTextMessage(String recipientId, String text) {
        try {
            final Recipient recipient = Recipient.newBuilder().recipientId(recipientId).build();
            final NotificationType notificationType = NotificationType.REGULAR;
            final String metadata = "DEVELOPER_DEFINED_METADATA";

            this.sendClient.sendTextMessage(recipient, notificationType, text, metadata);
        } catch (MessengerApiException | MessengerIOException e) {
            handleSendException(e);
        }
    }
    
    private void sendInfoTextMessage(String recipientId) {
        try {
        	 String text = "The recomended amount of water per day is eight" + 
        			 " 8-ounce glases, withc equals about 2 liters or half a galoon.";
            final Recipient recipient = Recipient.newBuilder().recipientId(recipientId).build();
            final NotificationType notificationType = NotificationType.REGULAR;
            final String metadata = "DEVELOPER_DEFINED_METADATA";

            this.sendClient.sendTextMessage(recipient, notificationType, text, metadata);
        } catch (MessengerApiException | MessengerIOException e) {
            handleSendException(e);
        }
    }
    
    private void handleSendException(Exception e) {
        logger.error("Message could not be sent. An unexpected error occurred.", e);
    }

    private void handleIOException(Exception e) {
        logger.error("Could not open Spring.io page. An unexpected error occurred.", e);
    }
}