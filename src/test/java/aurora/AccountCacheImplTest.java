package aurora;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Unit test for aurora.AccountCacheImpl
 */
public class AccountCacheImplTest {

  private AccountCacheImpl accountCache;
  private Consumer<Account> accountListenerMock;
  private Random random;
  private final Lock printLock = new ReentrantLock();
  private final Lock updateLock = new ReentrantLock();

  @Before
  public void setUp() {
    accountCache = new AccountCacheImpl(5);
    accountListenerMock = mock(Consumer.class);
    accountCache.subscribeForAccountUpdates(accountListenerMock);
    random = new Random();
  }

  @Test
  public void testUpdateExistingAccount() {
    Account account1 = new Account(1, 1000);
    accountCache.putAccount(account1);

    Account account2 = new Account(1, 2000); // Same id but updated balance
    accountCache.putAccount(account2);
    verify(accountListenerMock).accept(account2);

    assertEquals(account2, accountCache.getAccountById(1));
  }

  @Test
  public void testGetAccountById() {
    Account account = new Account(1, 1000);
    accountCache.putAccount(account);
    assertEquals(account, accountCache.getAccountById(1));
  }

  @Test
  public void testGetNonExistentAccountById() {
    assertNull(accountCache.getAccountById(999));
  }

  @Test
  public void testGetTop3AccountsByBalanceLessThan3() {
      Account account1 = new Account(1, 1000);
      Account account2 = new Account(2, 2000);
      accountCache.putAccount(account1);
      accountCache.putAccount(account2);

      assertEquals(2, accountCache.getTop3AccountsByBalance().size());
  }

  @Test
  public void testGetTop3AccountsByBalanceGreaterThan3() {
    Account account1 = new Account(1, 1000);
    Account account2 = new Account(2, 2000);
    Account account3 = new Account(3, 3000);
    Account account4 = new Account(4, 4000);
    Account account5 = new Account(5, 5000);
    accountCache.putAccount(account1);
    accountCache.putAccount(account2);
    accountCache.putAccount(account3);
    accountCache.putAccount(account4);
    accountCache.putAccount(account5);

    assertEquals(3, accountCache.getTop3AccountsByBalance().size());
  }

  @Test
  public void testGetTop3AccountsByBalanceWithNoAccounts() {
    assertEquals(0, accountCache.getTop3AccountsByBalance().size());
  }

  @Test
  public void testGetAccountByIdHitCountWithAccess() {
    Account account = new Account(1, 1000);
    accountCache.putAccount(account);
    accountCache.getAccountById(1);
    assertEquals(1, accountCache.getAccountByIdHitCount());
  }

  @Test
  public void testGetAccountByIdHitCountWithNoAccess() {
    Account account = new Account(1, 1000);
    accountCache.putAccount(account);
    assertEquals(0, accountCache.getAccountByIdHitCount());
  }

  @Test
  public void testManyPutAndGet() {
    List<Account> allAccounts = new ArrayList<Account>();
    for (int i = 0; i < 100; i++) {
      int accountId = i;
      Account account = new Account(accountId, this.generateRandomBalance());
      accountCache.putAccount(account);
      assertTop3AccountsAreCorrect(account, allAccounts);
      assertEquals(account, accountCache.getAccountById(accountId));
    }
  }

  @Test
  public void testzMultiThreadedPutAndGet() throws InterruptedException {
    List<Account> allAccounts = new ArrayList<Account>();
    List<List<List<Account>>> top3AccountResults = new ArrayList<List<List<Account>>>();
    ExecutorService executor = Executors.newFixedThreadPool(50);
    for (int i = 0; i < 1000; i++) {
      int accountId = i;
      executor.submit(() -> {
        Account account = new Account(accountId, this.generateRandomBalance());
        updateLock.lock();
        try {
          accountCache.putAccount(account);
          List<List<Account>> top3AccountResult = assertTop3AccountsAreCorrect(account, allAccounts);
          top3AccountResults.add(top3AccountResult);
        } finally {
          updateLock.unlock();
        }
      });
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);
    for (List<List<Account>> top3AccountPair: top3AccountResults) {
      assertEquals(top3AccountPair.size(), 2);
      List<Account> expectedTop3Accounts = top3AccountPair.get(0);
      List<Account> actualTop3Accounts = top3AccountPair.get(1);
      assertEquals(expectedTop3Accounts, actualTop3Accounts);
    }
  }

  private int generateRandomBalance() {
    return random.nextInt(999001) + 1000;
  }

  private void printTop3Accounts(List<Account> expectedTop3Accounts, List<Account> actualTop3Accounts) {
    printLock.lock();
    try {
      System.out.println("Expected Top 3 Accounts:");
      for (Account entry : expectedTop3Accounts) {
        System.out.println("ID: " + entry.id + ", Balance: " + entry.getBalance());
      }

      System.out.println("Actual Top 3 Accounts:");
      for (Account account : actualTop3Accounts) {
        System.out.println("ID: " + account.id + ", Balance: " + account.getBalance());
      }
    } finally {
      printLock.unlock();
    }
  }

  private List<List<Account>> assertTop3AccountsAreCorrect(Account newAccount, List<Account> allAccounts) {
    List<Account> actualTop3Accounts = accountCache.getTop3AccountsByBalance();
    allAccounts.add(newAccount);
    List<Account> expectedTop3Accounts = getExpectedTop3Accounts(allAccounts);
    printTop3Accounts(expectedTop3Accounts, actualTop3Accounts);
    assertEquals(expectedTop3Accounts, actualTop3Accounts);
    List<List<Account>> result = new ArrayList<List<Account>>();
    result.add(cloneListAccount(expectedTop3Accounts));
    result.add(cloneListAccount(actualTop3Accounts));
    return result;
  }

  private List<Account> cloneListAccount(List<Account> source) {
    List<Account> dest = new ArrayList<Account>();
    for (Account acc : source) {
      dest.add(new Account(acc.id, acc.getBalance()));
    }
    return dest;
  }

  private List<Account> getExpectedTop3Accounts(List<Account> allAccounts) {
    allAccounts.sort(Comparator.comparingLong(Account::getBalance).reversed());
    return allAccounts.subList(0, Math.min(3, allAccounts.size()));
  }
}
