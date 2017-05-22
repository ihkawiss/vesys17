package bank.commands;

import java.io.Serializable;

public class DepositCmd implements Serializable {

	private static final long serialVersionUID = 5775009673793921781L;

	private double amount;
	private double newBalance;
	private String accountNr;

	private boolean error = false;

	public DepositCmd(String accountNr, double amount) {
		this.accountNr = accountNr;
		this.amount = amount;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public double getNewBalance() {
		return newBalance;
	}

	public void setNewBalance(double newBalance) {
		this.newBalance = newBalance;
	}

	public String getAccountNr() {
		return accountNr;
	}

	public void setAccountNr(String accountNr) {
		this.accountNr = accountNr;
	}

	public boolean isError() {
		return error;
	}

	public void setError(boolean error) {
		this.error = error;
	}

}
