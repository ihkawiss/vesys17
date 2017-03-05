package bank.commands;

import java.io.Serializable;

public class WithdrawCmd implements Serializable {

	private static final long serialVersionUID = 7390711267844011731L;
	
	private double amount;
	private double newBalance;
	private String accountNr;

	// exception handling
	private boolean error = false;
	private String errMsg = null;

	public WithdrawCmd(String accountNr, double amount) {
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

	public String getErrMsg() {
		return errMsg;
	}

	public void setErrMsg(String errMsg) {
		this.errMsg = errMsg;
	}

}
