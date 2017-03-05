package server.connectivity;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

	private final int PORT = 1337;

	private ServerSocket socket;
	private Bank bank;

	public BankServer() {
		try {
			bank = new Bank();

			socket = new ServerSocket(PORT);
			System.out.println("BankServer started on port " + PORT);

			while (true)
				handleRequest();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Processes requests send by clients.
	 */
	private void handleRequest() {
		try {
			Socket s = socket.accept();

			LOG("");
			LOG("New request received from : " + s.getInetAddress().toString());

			ObjectInputStream in = new ObjectInputStream(s.getInputStream());
			ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());

			Object obj = in.readObject();

			if (obj instanceof NewAccountCmd) {

				NewAccountCmd cmd = (NewAccountCmd) obj;

				// create local account and set number on command
				cmd.setAccountNumber(bank.createAccount(cmd.getOwner()));

				LOG("Created new account for : " + cmd.getOwner() + " - accountNr: " + cmd.getAccountNumber());

				// write back to client
				out.writeObject(cmd);

			} else if (obj instanceof GetAccountCmd) {

				GetAccountCmd cmd = (GetAccountCmd) obj;

				// get account from repository
				Account account = bank.getAccount(cmd.getNumber());

				// set relevant data on command
				if (account != null) {
					cmd.setBalance(account.getBalance());
					cmd.setActive(account.isActive());
					cmd.setOwner(account.getOwner());
					cmd.setAccountFound(true);
					
					LOG("Send account details of accounrNr: " + cmd.getNumber());
					
				} else {
					ERR("Requested accountNr could not be found!");
				}

				// write back to client
				out.writeObject(cmd);

			} else if (obj instanceof GetAccountNumbersCmd) {

				GetAccountNumbersCmd cmd = (GetAccountNumbersCmd) obj;

				// set current account numbers
				cmd.setAccounts(bank.getAccountNumbers());

				LOG("Send current account list to client.");

				// write back to client
				out.writeObject(cmd);

			} else if (obj instanceof DepositCmd) {

				DepositCmd cmd = (DepositCmd) obj;

				Account account = bank.getAccount(cmd.getAccountNr());

				LOG("Deposit of " + cmd.getAmount() + " requested on accountNr: " + cmd.getAccountNr());

				try {
					account.deposit(cmd.getAmount());
					cmd.setNewBalance(account.getBalance());
					LOG("Deposit passed, new balance " + account.getBalance());
				} catch (InactiveException e) {
					ERR("Deposit failed with InactiveException!");
					cmd.setError(true);
				}

				// write back to client
				out.writeObject(cmd);

			} else if (obj instanceof WithdrawCmd) {

				WithdrawCmd cmd = (WithdrawCmd) obj;

				Account account = bank.getAccount(cmd.getAccountNr());

				LOG("Withdraw of " + cmd.getAmount() + " requested on accountNr: " + cmd.getAccountNr());

				try {
					account.withdraw(cmd.getAmount());
					cmd.setNewBalance(account.getBalance());
					LOG("Withdraw passed, new balance " + account.getBalance());
				} catch (InactiveException e) {
					cmd.setError(true);
					cmd.setErrMsg("InactiveException");
					ERR("Withdraw failed with InactiveException!");
				} catch (OverdrawException e) {
					cmd.setError(true);
					cmd.setErrMsg("OverdrawException");
					ERR("Withdraw failed with OverdrawException!");
				}

				// write back to client
				out.writeObject(cmd);

			} else if (obj instanceof CloseAccountCmd) {

				CloseAccountCmd cmd = (CloseAccountCmd) obj;

				LOG("Close account requested for " + cmd.getAccountNr());

				boolean closed = bank.closeAccount(cmd.getAccountNr());

				if (closed)
					LOG("Account " + cmd.getAccountNr() + " has been closed!");
				else
					ERR("Account " + cmd.getAccountNr() + " could not be closed!");

				cmd.setResult(closed);

				// write back to client
				out.writeObject(cmd);

			} else if (obj instanceof TransferCmd) {

				TransferCmd cmd = (TransferCmd) obj;

				Account from = bank.getAccount(cmd.getFromAccountNr());
				Account to = bank.getAccount(cmd.getToAccountNr());

				LOG("Transfer of " + cmd.getAmount() + " requested [from: " + from.getNumber() + ", to: "
						+ to.getNumber() + "]");

				try {
					bank.transfer(from, to, cmd.getAmount());
					LOG("Transfer passed, new balances [from: " + from.getBalance() + ", to: " + to.getBalance() + "]");
				} catch (InactiveException e) {
					cmd.setError(true);
					cmd.setErrMsg("InactiveException");
					ERR("Transfer failed with InactiveException");
				} catch (OverdrawException e) {
					cmd.setError(true);
					cmd.setErrMsg("OverdrawException");
					ERR("Transfer failed with OverdrawException");
				} catch (IllegalArgumentException e) {
					cmd.setError(true);
					cmd.setErrMsg("IllegalArgumentException");
					ERR("Transfer failed with IllegalArgumentException");
				}

				// write back to client
				out.writeObject(cmd);
			}

			s.close();

		} catch (EOFException e) {
			// happends on test connection
			// TODO [kki]: fix it / handle the right way
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public Bank getBank() {
		return bank;
	}

	public void setBank(Bank bank) {
		this.bank = bank;
	}

	private final void LOG(String s) {
		System.out.println(s);
	}

	private final void ERR(String s) {
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
				throw new IllegalArgumentException("Can't transfer negativ values!");

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
