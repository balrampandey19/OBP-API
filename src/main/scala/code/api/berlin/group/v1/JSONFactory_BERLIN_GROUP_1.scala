package code.api.berlin.group.v1

import java.util.Date

import code.api.util.APIUtil
import code.api.v2_1_0.IbanJson
import code.model.{CoreAccount, ModeratedBankAccount, ModeratedTransaction}
import code.transactionrequests.TransactionRequests.TransactionRequest

import scala.collection.immutable.List

object JSONFactory_BERLIN_GROUP_1 {

  implicit val formats = net.liftweb.json.DefaultFormats

  trait links
  case class Balances(balances: String) extends links
  case class Transactions(trasactions: String) extends links
  case class ViewAccount(viewAccount: String) extends links
  case class CoreAccountJsonV1(
                                 id: String,
                                 iban: String,
                                 currency: String,
                                 accountType: String,
                                 cashAccountType: String,
                                 _links: List[links],
                                 name: String
                               )

  case class CoreAccountsJsonV1(`account-list`: List[CoreAccountJsonV1])
  
  case class AmountOfMoneyV1(
    currency : String,
    content : String
  )
  case class ClosingBookedBody(
    amount : AmountOfMoneyV1,
    date: Date
  )
  case class ExpectedBody(
    amount : AmountOfMoneyV1,
    lastActionDateTime: Date
  )
  case class AccountBalance(
    closingBooked: ClosingBookedBody,
    expected: ExpectedBody
  )
  case class AccountBalances(`balances`: List[AccountBalance])
  
  case class TransactionsJsonV1(
    transactions_booked: List[TransactionJsonV1],
    transactions_pending: List[TransactionJsonV1],
    _links: List[ViewAccount]
  )
  
  case class TransactionJsonV1(
    transactionId: String,
    creditorName: String,
    creditorAccount: IbanJson,
    amount: AmountOfMoneyV1,
    bookingDate: Date,
    valueDate: Date,
    remittanceInformationUnstructured: String
  )

  def createTransactionListJSON(coreAccounts: List[CoreAccount]) = {
    CoreAccountsJsonV1(coreAccounts.map(
      x => CoreAccountJsonV1(
        id = x.id,
        iban = if (x.account_routing.scheme == "IBAN") x.account_routing.address else "",
        currency = "",
        accountType = "",
        cashAccountType = "",
        _links = Balances(s"/${OBP_BERLIN_GROUP_1.version}/accounts/${x.id}/balances") :: Transactions(s"/${OBP_BERLIN_GROUP_1.version}/accounts/${x.id}/transactions") :: Nil,
        name = x.label)
       )
    )
  }

  def createAccountBalanceJSON(moderatedAccount : ModeratedBankAccount, transactionRequests: List[TransactionRequest] ) = {
    //get the latest end_date of `COMPLETED` transactionRequests
    val latestCompletedEndDate = transactionRequests.filter(_.status =="COMPLETED").map(_.end_date).max
  
    //get the latest end_date of !`COMPLETED` transactionRequests
    val latestUncompletedEndDate = transactionRequests.filter(_.status !="COMPLETED").map(_.end_date).max
    
    //get the SUM of the amount of all !`COMPLETED` transactionRequests
    val sumOfAllUncompletedTransactionrequests = transactionRequests.filter(_.status !="COMPLETED").map(_.body.value.amount).map(BigDecimal(_)).sum
    //sum of the unCompletedTransactions and the account.balance is the current expectd amount:
    val sumOfAll = (BigDecimal(moderatedAccount.balance) + sumOfAllUncompletedTransactionrequests).toString()
    
    AccountBalances(
      AccountBalance(
        closingBooked = ClosingBookedBody(amount = AmountOfMoneyV1(currency=moderatedAccount.currency.getOrElse(""), content = moderatedAccount.balance ), date = latestCompletedEndDate),
        expected = ExpectedBody (amount = AmountOfMoneyV1(currency=moderatedAccount.currency.getOrElse(""), content = sumOfAll), lastActionDateTime = latestUncompletedEndDate)
      ) :: Nil
    )
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV1 = {
    TransactionJsonV1(
      transactionId = transaction.id.value,
      creditorName = "",
      creditorAccount = IbanJson(APIUtil.stringOptionOrNull(transaction.bankAccount.get.iban)),
      amount = AmountOfMoneyV1(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      bookingDate = transaction.startDate.get,
      valueDate = transaction.finishDate.get,
      remittanceInformationUnstructured = APIUtil.stringOptionOrNull(transaction.description)
    )
  }
  
  def createTransactionFromRequestJSON(transactionRequest : TransactionRequest) : TransactionJsonV1 = {
    TransactionJsonV1(
      transactionId = transactionRequest.id.value,
      creditorName = transactionRequest.name,
      creditorAccount = IbanJson(transactionRequest.from.account_id),
      amount = AmountOfMoneyV1(transactionRequest.charge.value.currency, transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      valueDate = transactionRequest.end_date,
      remittanceInformationUnstructured = transactionRequest.body.description
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV1 = {
    TransactionsJsonV1(
      transactions_booked =transactions.map(createTransactionJSON),
      transactions_pending =transactionRequests.filter(_.status!="COMPLETED").map(createTransactionFromRequestJSON),
      _links = ViewAccount(s"/${OBP_BERLIN_GROUP_1.version}/accounts/${transactionRequests.head.from.account_id}/balances")::Nil
    )
  }

}
