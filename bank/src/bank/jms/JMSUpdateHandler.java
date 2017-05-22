package bank.jms;

import bank.BankDriver2;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by benjamin on 29.05.2015.
 */
public class JMSUpdateHandler implements MessageListener {

    private List<BankDriver2.UpdateHandler> handlers = new LinkedList<>();
    private Context jndiContext;
    private ConnectionFactory factory;
    private Topic topic;

    public JMSUpdateHandler() throws NamingException {
    	Hashtable<String, String> properties = new Hashtable<>();
		properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
		properties.put(Context.PROVIDER_URL, "jnp://localhost:1099");
		properties.put("queue.BANK", "bank.BANK");
		properties.put("topic.BANK.LISTENER", "bank.BANK.LISTENER");
    	
        jndiContext = new InitialContext(properties);
        factory = (ConnectionFactory) jndiContext.lookup("ConnectionFactory");
        topic = (Topic) jndiContext.lookup("/topic/BANK");
    }

    public void registerUpdateHandler(BankDriver2.UpdateHandler handler) {
        handlers.add(handler);
    }

    public void start() {
        JMSContext context = factory.createContext();
        context.start();
        JMSConsumer subscriber = context.createConsumer(topic);
        subscriber.setMessageListener(this);
    }

    @Override
    public void onMessage(Message message) {
        try {
            String nr = message.getBody(String.class);
            for(BankDriver2.UpdateHandler handler : handlers){
                handler.accountChanged(nr);
            }
        } catch (IOException | JMSException e) {
            e.printStackTrace();
        }
    }
}