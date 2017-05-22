package server.connectivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import bank.InactiveException;
import bank.OverdrawException;
import bank.commands.CloseAccountCmd;
import bank.commands.DepositCmd;
import bank.commands.GetAccountCmd;
import bank.commands.GetAccountNumbersCmd;
import bank.commands.NewAccountCmd;
import bank.commands.TransferCmd;
import bank.commands.WithdrawCmd;

/**
 * This class acts as the main bank server.
 *
 * @author Kevin Kirn <kevin.kirn@students.fhnw.ch>
 * @author Hoang Tran <hoang.tran@students.fhnw.ch>
 */
public class BankServer {

	private Bank bank;
	private static ConnectionFactory factory;
	private static Topic topic;

	public BankServer() {
		bank = new Bank();

		System.out.println("JMS BankServer started");

		handleRequest();
	}

	/**
	 * Processes requests send by clients.
	 * 
	 * @throws NamingException
	 */
	private void handleRequest() {

		try {
			Hashtable<String, String> properties = new Hashtable<>();
			properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
			properties.put(Context.PROVIDER_URL, "localhost:1099");
			properties.put("queue.BANK", "bank.BANK");
			properties.put("topic.BANK.LISTENER", "bank.BANK.LISTENER");

			final Context jndiContext = new InitialContext(properties);
			factory = (ConnectionFactory) jndiContext.lookup("ConnectionFactory");
			final Queue queue = (Queue) jndiContext.lookup("/queue/BANK");
			topic = (Topic) jndiContext.lookup("/topic/BANK");

			try (JMSContext context = factory.createContext()) {

				JMSConsumer consumer = context.createConsumer(queue);
				JMSProducer sender = context.createProducer();

				System.out.println("JMS service running...");

				while (true) {
					Message req = consumer.receive();
					String message = req.getBody(String.class);

					// transform to command and execute
					Object command = deserialize(message);

					Serializable responseCommand = null;

					if (command instanceof NewAccountCmd) {
						responseCommand = handleNewAccountCommand((NewAccountCmd) command);

						sendUpdate(((NewAccountCmd) responseCommand).getAccountNumber());

					} else if (command instanceof GetAccountCmd) {
						responseCommand = handleGetAccountCommand((GetAccountCmd) command);

					} else if (command instanceof GetAccountNumbersCmd) {
						responseCommand = handleGetAccountNumbersCommand((GetAccountNumbersCmd) command);

					} else if (command instanceof DepositCmd) {
						responseCommand = handleDepositCommand((DepositCmd) command);

						sendUpdate(((WithdrawCmd) responseCommand).getAccountNr());
					} else if (command instanceof WithdrawCmd) {
						responseCommand = handleWithdrawCommand((WithdrawCmd) command);

						sendUpdate(((WithdrawCmd) responseCommand).getAccountNr());

					} else if (command instanceof CloseAccountCmd) {
						responseCommand = handleCloseAccountCommand((CloseAccountCmd) command);

						sendUpdate(((CloseAccountCmd) responseCommand).getAccountNr());

					} else if (command instanceof TransferCmd) {
						responseCommand = handleTransferCommand((TransferCmd) command);

						sendUpdate(((TransferCmd) responseCommand).getFromAccountNr());
						sendUpdate(((TransferCmd) responseCommand).getToAccountNr());
					}

					// send answer to client
					sender.send(req.getJMSReplyTo(), serialize(responseCommand));
				}

			} catch (Exception e) {
				System.out.println("Something went wrong...");
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static String serialize(Serializable o) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(o);
		oos.close();
		return Base64.getEncoder().encodeToString(baos.toByteArray());
	}

	private static Object deserialize(String s) throws IOException, ClassNotFoundException {
		byte[] data = Base64.getDecoder().decode(s);
		ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
		Object o = ois.readObject();
		ois.close();
		return o;
	}

	private static void sendUpdate(String number) {
		try (JMSContext context = factory.createContext()) {
			JMSProducer publisher = context.createProducer();
			publisher.send(topic, number);
		}
	}

	private Serializable handleNewAccountCommand(NewAccountCmd cmd) throws IOException {
		// create local account and set number on command
		cmd.setAccountNumber(bank.createAccount(cmd.getOwner()));

		log("Created new account for : " + cmd.getOwner() + " - accountNr: " + cmd.getAccountNumber());

		return cmd;
	}

	private Serializable handleGetAccountCommand(GetAccountCmd cmd) throws IOException {
		// get account from repository
		Account account = bank.getAccount(cmd.getNumber());

		// set relevant data on command
		if (account != null) {
			cmd.setBalance(account.getBalance());
			cmd.setActive(account.isActive());
			cmd.setOwner(account.getOwner());
			cmd.setAccountFound(true);

			log("Send account details of accountNr: " + cmd.getNumber());

		} else {
			err("Requested accountNr could not be found!");
		}

		return cmd;
	}

	private Serializable handleGetAccountNumbersCommand(GetAccountNumbersCmd cmd) throws IOException {
		// set current account numbers
		cmd.setAccounts(bank.getAccountNumbers());

		log("Send current account list to client.");

		return cmd;
	}

	private Serializable handleDepositCommand(DepositCmd cmd) throws IOException {
		Account account = bank.getAccount(cmd.getAccountNr());

		log("Deposit of " + cmd.getAmount() + " requested on accountNr: " + cmd.getAccountNr());

		try {
			account.deposit(cmd.getAmount());
			cmd.setNewBalance(account.getBalance());
			log("Deposit passed, new balance " + account.getBalance());
		} catch (InactiveException e) {
			err("Deposit failed with InactiveException!");
			cmd.setError(true);
		}

		return cmd;
	}

	private Serializable handleWithdrawCommand(WithdrawCmd cmd) throws IOException {
		Account account = bank.getAccount(cmd.getAccountNr());

		log("Withdraw of " + cmd.getAmount() + " requested on accountNr: " + cmd.getAccountNr());

		try {
			account.withdraw(cmd.getAmount());
			cmd.setNewBalance(account.getBalance());
			log("Withdraw passed, new balance " + account.getBalance());
		} catch (InactiveException e) {
			cmd.setError(true);
			cmd.setErrMsg("InactiveException");
			err("Withdraw failed with InactiveException!");
		} catch (OverdrawException e) {
			cmd.setError(true);
			cmd.setErrMsg("OverdrawException");
			err("Withdraw failed with OverdrawException!");
		}

		return cmd;
	}

	private Serializable handleCloseAccountCommand(CloseAccountCmd cmd) throws IOException {
		log("Close account requested for " + cmd.getAccountNr());

		boolean closed = bank.closeAccount(cmd.getAccountNr());

		if (closed)
			log("Account " + cmd.getAccountNr() + " has been closed!");
		else
			err("Account " + cmd.getAccountNr() + " could not be closed!");

		cmd.setResult(closed);

		return cmd;
	}

	private Serializable handleTransferCommand(TransferCmd cmd) throws IOException {
		Account from = bank.getAccount(cmd.getFromAccountNr());
		Account to = bank.getAccount(cmd.getToAccountNr());

		log("Transfer of " + cmd.getAmount() + " requested [from: " + from.getNumber() + ", to: " + to.getNumber()
				+ "]");

		try {
			bank.transfer(from, to, cmd.getAmount());
			log("Transfer passed, new balances [from: " + from.getBalance() + ", to: " + to.getBalance() + "]");
		} catch (InactiveException e) {
			cmd.setError(true);
			cmd.setErrMsg("InactiveException");
			err("Transfer failed with InactiveException");
		} catch (OverdrawException e) {
			cmd.setError(true);
			cmd.setErrMsg("OverdrawException");
			err("Transfer failed with OverdrawException");
		} catch (IllegalArgumentException e) {
			cmd.setError(true);
			cmd.setErrMsg("IllegalArgumentException");
			err("Transfer failed with IllegalArgumentException");
		}

		return cmd;
	}

	public Bank getBank() {
		return bank;
	}

	public void setBank(Bank bank) {
		this.bank = bank;
	}

	private void log(String s) {
		System.out.println(s);
	}

	private void err(String s) {
		System.err.println(s);
	}

	static class Bank implements bank.Bank {

		private final Map<String, Account> accounts = new HashMap<>();

		@Override
		public Set<String> getAccountNumbers() {
			Set<String> accountNumbers = new HashSet<>();
			for (Map.Entry<String, Account> entry : accounts.entrySet()) {
				if (entry.getValue().isActive())
					accountNumbers.add(entry.getKey());
			}

			return accountNumbers;
		}

		@Override
		public String createAccount(String owner) {
			Account newAccount = new Account(owner);
			accounts.put(newAccount.getNumber(), newAccount);

			return newAccount.getNumber();
		}

		@Override
		public boolean closeAccount(String number) {

			if (!accounts.containsKey(number))
				return false;

			Account account = accounts.get(number);

			if (account.isActive() && account.getBalance() == 0) {
				account.active = false;
				return true;
			}

			return false;
		}

		@Override
		public Account getAccount(String number) {
			return accounts.get(number);
		}

		@Override
		public void transfer(bank.Account from, bank.Account to, double amount)
				throws IOException, InactiveException, OverdrawException, IllegalArgumentException {

			if (amount < 0)
				throw new IllegalArgumentException("Can't transfer negative values!");

			from.withdraw(amount);
			to.deposit(amount);
		}

	}

	static class Account implements bank.Account, Serializable {

		private static final long serialVersionUID = 1112843626758025830L;

		private String number;
		private String owner;
		private double balance;
		private boolean active = true;

		Account(String owner) {
			this.owner = owner;
			this.number = UUID.randomUUID().toString();
		}

		@Override
		public double getBalance() {
			return balance;
		}

		@Override
		public String getOwner() {
			return owner;
		}

		@Override
		public String getNumber() {
			return number;
		}

		@Override
		public boolean isActive() {
			return active;
		}

		@Override
		public void deposit(double amount) throws InactiveException {
			if (!isActive())
				throw new InactiveException("Can't deposit on inactive account");

			if (amount > 0)
				this.balance += amount;
		}

		@Override
		public void withdraw(double amount) throws InactiveException, OverdrawException {
			if (!isActive())
				throw new InactiveException("Can't withdraw on inactive account");

			if (balance < amount)
				throw new OverdrawException("Insufficient balance");

			this.balance -= amount;
		}

	}
}
