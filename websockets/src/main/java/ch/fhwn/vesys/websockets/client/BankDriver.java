package ch.fhwn.vesys.websockets.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;

import bank.BankDriver2;
import bank.InactiveException;
import bank.OverdrawException;
import bank.commands.CloseAccountCmd;
import bank.commands.DepositCmd;
import bank.commands.GetAccountCmd;
import bank.commands.GetAccountNumbersCmd;
import bank.commands.NewAccountCmd;
import bank.commands.TransferCmd;
import bank.commands.WithdrawCmd;

public class BankDriver implements BankDriver2 {

	static Session session = null;
	private Bank bank = null;

	@Override
	public void connect(String[] args) throws IOException {

		ClientManager client = ClientManager.createClient();

		final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

		try {
			session = client.connectToServer(ClientWebSocketEndpoint.class, cec,
					new URI("ws://localhost:8888/server/bank"));

			// I can't get a active connection and can't find the issue
			// Caused by: java.net.ConnectException: Connection refused: no further information

		} catch (DeploymentException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void disconnect() throws IOException {
		if (session != null && session.isOpen())
			session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Bye"));
	}

	@Override
	public Bank getBank() {
		return bank;
	}

	@Override
	public void registerUpdateHandler(UpdateHandler handler) throws IOException {

	}

	static class Bank implements bank.Bank {
		
		private final Map<String, Account> accounts = new HashMap<>();

		public Bank(InetAddress host, int port) {
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

			session.getBasicRemote().sendText(serialize(cmd));

			return null; // TODO: get appropriate answer
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

	private static Object deserialize(String s) throws IOException, ClassNotFoundException {
		byte[] data = Base64.getDecoder().decode(s);
		ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
		Object o = ois.readObject();
		ois.close();
		return o;
	}

}
