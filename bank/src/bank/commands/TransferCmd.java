package bank.commands;

import java.io.Serializable;

public class TransferCmd implements Serializable {

	private static final long serialVersionUID = -7729358566384949105L;

	private String fromAccountNr;
	private String toAccountNr;
	private double amount;
	private double balanceFromAccount;
	private double balanceToAccount;

	private boolean error = false;
	private String errMsg = null;

	public double getBalanceFromAccount() {
		return balanceFromAccount;
	}

	public void setBalanceFromAccount(double balanceFromAccount) {
		this.balanceFromAccount = balanceFromAccount;
	}

	public double getBalanceToAccount() {
		return balanceToAccount;
	}

	public void setBalanceToAccount(double balanceToAccount) {
		this.balanceToAccount = balanceToAccount;
	}

	public String getErrMsg() {
		return errMsg;
	}

	public void setErrMsg(String errMsg) {
		this.errMsg = errMsg;
	}

	public TransferCmd(String fromAccountNr, String toAccountNr, double amount) {
		this.fromAccountNr = fromAccountNr;
		this.toAccountNr = toAccountNr;
		this.amount = amount;
	}

	public String getFromAccountNr() {
		return fromAccountNr;
	}

	public void setFromAccountNr(String fromAccountNr) {
		this.fromAccountNr = fromAccountNr;
	}

	public String getToAccountNr() {
		return toAccountNr;
	}

	public void setToAccountNr(String toAccountNr) {
		this.toAccountNr = toAccountNr;
	}

	public boolean hasError() {
		return error;
	}

	public void setError(boolean error) {
		this.error = error;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

}
