package bank.jms;

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

import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
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
 * HTTP bank driver implementation.
 * 
 * @author Kevin Kirn <kevin.kirn@students.fhnw.ch>
 * @author Hoang Tran <hoang.tran@students.fhnw.ch>
 */
public class Driver implements bank.BankDriver2 {

	private Bank bank = null;
	private JMSUpdateHandler handler;
	
	@Override
	public void connect(String[] args) throws IOException {

		bank = new Bank();

		try {
			handler = new JMSUpdateHandler();
			handler.start();
		} catch (NamingException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void disconnect() throws IOException {
		bank = null;
	}

	@Override
	public Bank getBank() {
		return bank;
	}

	static class Bank implements bank.Bank {

		private final Map<String, Account> accounts = new HashMap<>();

		private ConnectionFactory conFactory;
		private Context context;
		private Queue queue;
		
		public Bank() {
			try {
				Hashtable<String, String> properties = new Hashtable<>();
				properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
				properties.put(Context.PROVIDER_URL, "localhost:1099");
				
				context = new InitialContext(properties);
				conFactory = (ConnectionFactory) context.lookup("ConnectionFactory");
				queue = (Queue) context.lookup("/queue/BANK");
			} catch (NamingException e) {
				e.printStackTrace();
			}
		}

		@Override
		public String createAccount(String owner) throws IOException {

			// create new account on bank server
			Object obj = sendCommand(new NewAccountCmd(owner));

			if (obj instanceof NewAccountCmd) {
				NewAccountCmd response = (NewAccountCmd) obj;

				// save local copy of account
				Account newAccount = new Account(response.getOwner(), response.getAccountNumber(), this);
				accounts.put(newAccount.getNumber(), newAccount);

				return newAccount.getNumber();
			}

			return null;
		}

		@Override
		public boolean closeAccount(String number) throws IOException {

			// create new account on bank server
			Object obj = sendCommand(new CloseAccountCmd(number));

			if (obj instanceof CloseAccountCmd) {

				CloseAccountCmd cmd = (CloseAccountCmd) obj;

				if (cmd.wasClosed()) {

					// update local copy
					accounts.get(number).active = false;

					return true;
				}

			}

			return false;
		}

		@Override
		public Set<String> getAccountNumbers() throws IOException {

			// only fetch server side account list since
			// getAccount is able to add non existing accounts on the fly.
			Object obj = sendCommand(new GetAccountNumbersCmd());

			if (obj instanceof GetAccountNumbersCmd) {
				Set<String> accountList = ((GetAccountNumbersCmd) obj).getAccounts();

				// update all accounts in list
				for (String accountNr : accountList)
					getAccount(accountNr);

				return accountList;
			}

			return new HashSet<String>();
		}

		@Override
		public Account getAccount(String number) throws IOException {

			// request account details from bank server
			Object obj = sendCommand(new GetAccountCmd(number));

			if (obj instanceof GetAccountCmd) {
				GetAccountCmd cmd = (GetAccountCmd) obj;

				// check if account exists on server
				if (!cmd.accountFound())
					return null;

				Account account;

				// add account to local bank or update existing one
				if (!accounts.containsKey(cmd.getNumber())) {
					account = new Account(cmd.getOwner(), cmd.getNumber(), this);
					accounts.put(account.getNumber(), account);
				} else {
					account = accounts.get(cmd.getNumber());
				}

				account.balance = cmd.getBalance();
				account.active = cmd.isActive();

				return account;
			}

			return null;
		}

		public Object sendCommand(Serializable cmd) throws IOException {

			try (JMSContext context = conFactory.createContext()) {
				TemporaryQueue tempQueue = context.createTemporaryQueue();

				JMSProducer sender = context.createProducer().setJMSReplyTo(tempQueue);
				JMSConsumer receiver = context.createConsumer(tempQueue);

				sender.send(queue, serialize(cmd));
				return receiver.receiveBody(Object.class);
			} catch (Exception e) {
				return null;
			}

		}

		@Override
		public void transfer(bank.Account a, bank.Account b, double amount)
				throws IOException, IllegalArgumentException, OverdrawException, InactiveException {

			// using deposit and withdraw is not 100% safe here
			Object obj = sendCommand(new TransferCmd(a.getNumber(), b.getNumber(), amount));

			if (obj instanceof TransferCmd) {

				TransferCmd cmd = (TransferCmd) obj;

				if (cmd.hasError()) {

					// throw passed exceptions on client
					if (cmd.getErrMsg().equals("InactiveException")) {
						throw new InactiveException("Can't deposit on inactive account");
					} else if (cmd.getErrMsg().equals("OverdrawException")) {
						throw new OverdrawException("Insufficient balance");
					} else if (cmd.getErrMsg().equals("IllegalArgumentException")) {
						throw new IllegalArgumentException("Can't transfer negativ values!");
					}

				} else {

					// update all accounts
					getAccountNumbers();
				}

			}
		}

	}

	public static class Account implements bank.Account {

		private String number;
		private String owner;
		private double balance;
		private boolean active = true;

		private final Bank bankRef;

		Account(String owner, String accountNr, Bank bankRef) {
			this.owner = owner;
			this.number = accountNr;
			this.bankRef = bankRef;
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

			try {

				// try to deposit on server side
				Object obj = bankRef.sendCommand(new DepositCmd(this.number, amount));

				if (obj instanceof DepositCmd) {
					DepositCmd cmd = (DepositCmd) obj;

					// update local value if deposit was successful
					if (!cmd.isError())
						this.balance = cmd.getNewBalance();
					else
						throw new InactiveException("Can't deposit on inactive account");
				}

			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		@Override
		public void withdraw(double amount) throws InactiveException, OverdrawException {

			try {

				// try to withdraw on server side
				Object obj = bankRef.sendCommand(new WithdrawCmd(this.number, amount));

				if (obj instanceof WithdrawCmd) {
					WithdrawCmd cmd = (WithdrawCmd) obj;

					// update local value if deposit was successful
					if (!cmd.isError())
						this.balance = cmd.getNewBalance();
					else {
						if (cmd.getErrMsg().equals("InactiveException"))
							throw new InactiveException("Can't withdraw on inactive account");
						else if (cmd.getErrMsg().equals("OverdrawException"))
							throw new OverdrawException("Insufficient balance");
					}

				}

			} catch (IOException e) {
				e.printStackTrace();
			}

		}

	}

	private static String serialize(Serializable o) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(o);
		oos.close();
		return Base64.getEncoder().encodeToString(baos.toByteArray());
	}

	@SuppressWarnings("unused")
	private static Object deserialize(String s) throws IOException, ClassNotFoundException {
		byte[] data = Base64.getDecoder().decode(s);
		ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
		Object o = ois.readObject();
		ois.close();
		return o;
	}

	@Override
	public void registerUpdateHandler(UpdateHandler handler) throws IOException {
		this.handler.registerUpdateHandler(handler);
	}

}
