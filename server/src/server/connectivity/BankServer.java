package server.connectivity;

import bank.InactiveException;
import bank.OverdrawException;
import bank.commands.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

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
			Socket socket = this.socket.accept();

			log("\nNew request received from: " + socket.getInetAddress().toString());

			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

			Object command = in.readObject();

			if (command instanceof NewAccountCmd) {
				command = handleNewAccountCommand((NewAccountCmd) command);

			} else if (command instanceof GetAccountCmd) {
				command = handleGetAccountCommand((GetAccountCmd) command);

			} else if (command instanceof GetAccountNumbersCmd) {
				command = handleGetAccountNumbersCommand((GetAccountNumbersCmd) command);

			} else if (command instanceof DepositCmd) {
				command = handleDepositCommand((DepositCmd) command);

			} else if (command instanceof WithdrawCmd) {
				command = handleWithdrawCommand((WithdrawCmd) command);

			} else if (command instanceof CloseAccountCmd) {
				command = handleCloseAccountCommand((CloseAccountCmd) command);

			} else if (command instanceof TransferCmd) {
				command = handleTransferCommand((TransferCmd) command);
			}

			// write back to client
			out.writeObject(command);

			socket.close();

		} catch (EOFException e) {
			// happends on test connection
			// TODO [kki]: fix it / handle the right way
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private Object handleNewAccountCommand(NewAccountCmd cmd) throws IOException {
		// create local account and set number on command
		cmd.setAccountNumber(bank.createAccount(cmd.getOwner()));

		log("Created new account for : " + cmd.getOwner() + " - accountNr: " + cmd.getAccountNumber());

		return cmd;
	}

	private Object handleGetAccountCommand(GetAccountCmd cmd) throws IOException {
		// get account from repository
		Account account = bank.getAccount(cmd.getNumber());

		// set relevant data on command
		if (account != null) {
			cmd.setBalance(account.getBalance());
			cmd.setActive(account.isActive());
			cmd.setOwner(account.getOwner());
			cmd.setAccountFound(true);

			log("Send account details of accounrNr: " + cmd.getNumber());

		} else {
			err("Requested accountNr could not be found!");
		}

		return cmd;
	}

	private Object handleGetAccountNumbersCommand(GetAccountNumbersCmd cmd) throws IOException {
		// set current account numbers
		cmd.setAccounts(bank.getAccountNumbers());

		log("Send current account list to client.");

		return cmd;
	}

	private Object handleDepositCommand(DepositCmd cmd) throws IOException {
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

	private Object handleWithdrawCommand(WithdrawCmd cmd) throws IOException {
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

	private Object handleCloseAccountCommand(CloseAccountCmd cmd) throws IOException {
		log("Close account requested for " + cmd.getAccountNr());

		boolean closed = bank.closeAccount(cmd.getAccountNr());

		if (closed)
			log("Account " + cmd.getAccountNr() + " has been closed!");
		else
			err("Account " + cmd.getAccountNr() + " could not be closed!");

		cmd.setResult(closed);

		return cmd;
	}

	private Object handleTransferCommand(TransferCmd cmd) throws IOException {
		Account from = bank.getAccount(cmd.getFromAccountNr());
		Account to = bank.getAccount(cmd.getToAccountNr());

		log("Transfer of " + cmd.getAmount() + " requested [from: " + from.getNumber() + ", to: "
                + to.getNumber() + "]");

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
