package bank.commands;

import java.io.Serializable;

public class GetAccountCmd implements Serializable {

	private static final long serialVersionUID = 3868467890334257742L;

	public GetAccountCmd(String number) {
		this.number = number;
	}

	private final String number;
	private double balance;
	private boolean isActive;
	private String owner;

	private boolean accountFound = false;

	public String getNumber() {
		return number;
	}

	public double getBalance() {
		return balance;
	}

	public void setBalance(double amount) {
		this.balance = amount;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public boolean accountFound() {
		return accountFound;
	}

	public void setAccountFound(boolean accountFound) {
		this.accountFound = accountFound;
	}

}
