/*
 * Copyright (c) 2000-2017 Fachhochschule Nordwestschweiz (FHNW)
 * All Rights Reserved. 
 */

package bank.local;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import bank.InactiveException;
import bank.OverdrawException;

/**
 * Local bank driver implementation.
 * 
 * @author Kevin Kirn <kevin.kirn@students.fhnw.ch>
 * @author Hoang Tran <hoang.tran@students.fhnw.ch>
 */
public class Driver implements bank.BankDriver {
	private Bank bank = null;

	@Override
	public void connect(String[] args) {
		bank = new Bank();
		System.out.println("connected...");
	}

	@Override
	public void disconnect() {
		bank = null;
		System.out.println("disconnected...");
	}

	@Override
	public Bank getBank() {
		return bank;
	}

	static class Bank implements bank.Bank {

		private final Map<String, Account> accounts = new HashMap<>();

		@Override
		public Set<String> getAccountNumbers() {
			Set<String> accountNumbers = new HashSet<>();
			for(Map.Entry<String, Account> entry : accounts.entrySet()){
				if(entry.getValue().isActive())
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
		public bank.Account getAccount(String number) {
			return accounts.get(number);
		}

		@Override
		public void transfer(bank.Account from, bank.Account to, double amount)
				throws IOException, InactiveException, OverdrawException {

			if(amount < 0)
				throw new IllegalArgumentException("Can't transfer negativ values!");
			
			from.withdraw(amount);
			to.deposit(amount);
		}

	}

	static class Account implements bank.Account {
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
			if(!isActive())
				throw new InactiveException("Can't deposit on inactive account");
			
			if(amount > 0)
				this.balance += amount;
		}

		@Override
		public void withdraw(double amount) throws InactiveException, OverdrawException {
			if(!isActive())
				throw new InactiveException("Can't withdraw on inactive account");
				
			if(balance < amount)
				throw new OverdrawException("Insufficient balance");
			
			this.balance -= amount;
		}

	}

}