package com.engagepoint.university.messaging.smpp;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.SmppProcessingException;
import com.engagepoint.university.messaging.dao.specific.SmsDAO;
import com.engagepoint.university.messaging.dto.SmsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Startup
public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    private ThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor monitorExecutor;
    private SmppServerConfiguration configuration;

    @Inject
    private SmsDTO smsDTO;
    @Inject
    private SmsDAO smsDAO;

    public ServerMain() {
        setExecutor();
        setConfiguration();
        setMonitorExecutor();
    }

    public void startSmppServer() throws Exception {
        DefaultSmppServer smppServer = new DefaultSmppServer(this.configuration, new DefaultSmppServerHandler(),
                this.executor, this.monitorExecutor);
        smppServer.start();
    }

    public void setExecutor() {
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }

    public void setMonitorExecutor() {
        this.monitorExecutor = (ScheduledThreadPoolExecutor)
                Executors.newScheduledThreadPool(1, new ThreadFactory() {
                    private AtomicInteger sequence = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("SmppServerSessionWindowMonitorPool-" + sequence.getAndIncrement());
                        return t;
                    }
                });
    }

    public void setConfiguration() {
        this.configuration = new SmppServerConfiguration();
        configuration.setPort(2776);
        configuration.setMaxConnectionSize(10);
        configuration.setNonBlockingSocketsEnabled(true);
        configuration.setDefaultRequestExpiryTimeout(30000);
        configuration.setDefaultWindowMonitorInterval(15000);
        configuration.setDefaultWindowSize(5);
        configuration.setDefaultWindowWaitTimeout(configuration.getDefaultRequestExpiryTimeout());
        configuration.setDefaultSessionCountersEnabled(true);
        configuration.setJmxEnabled(true);
    }

    public /*static*/ class DefaultSmppServerHandler implements SmppServerHandler {

        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, final BaseBind bindRequest) throws SmppProcessingException {
            sessionConfiguration.setName("Application.SMPP." + sessionConfiguration.getSystemId());
        }

        @Override
        public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
            logger.info("Session created: {}", session);
            // need to do something it now (flag we're ready)
            session.serverReady(new TestSmppSessionHandler(session));
        }

        @Override
        public void sessionDestroyed(Long sessionId, SmppServerSession session) {
            logger.info("Session destroyed: {}", session);
            if (session.hasCounters()) {
                logger.info(" final session rx-submitSM: {}", session.getCounters().getRxSubmitSM());
            }
            session.destroy();
        }
    }

    public /*static*/ class TestSmppSessionHandler extends DefaultSmppSessionHandler {

        private WeakReference<SmppSession> sessionRef;

        public TestSmppSessionHandler(SmppSession session) {
            this.sessionRef = new WeakReference<SmppSession>(session);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            SmppSession session = sessionRef.get();

            logger.info("PduRequest .getReferenceObject()" + pduRequest.getName());

            SubmitSm req = (SubmitSm) pduRequest;

            String str = CharsetUtil.decode(req.getShortMessage(), CharsetUtil.CHARSET_MODIFIED_UTF8);

            logger.info("SMS sender - " + req.getSourceAddress().getAddress());
            logger.info("SMS reciver - " + req.getDestAddress().getAddress());
            logger.info("SMS body in byte - " + req.getShortMessage());
            System.out.println("SMS reciver in server SMPP - " + req.getDestAddress().getAddress());
            System.out.println("SMS sender in server SMPP - " + req.getSourceAddress().getAddress());
            System.out.println("SMS body in server SMPP - " + str);

            logger.info("SMS body - " + str);

            // SmsDTO smsDTO = new SmsDTO();
            smsDTO.setBody(str);
            smsDTO.setSender(req.getSourceAddress().getAddress());
            smsDTO.setDeliveryDate(new Date());
            smsDTO.setSendDate(new Date());

            // SmsDAOImpl smsDAO = new SmsDAOImpl();
            smsDAO.save(smsDTO);

            // mimic how long processing could take on a slower smsc
            try {
                //Thread.sleep(50);
            } catch (Exception e) {
            }

            return pduRequest.createResponse();
        }
    }

}
