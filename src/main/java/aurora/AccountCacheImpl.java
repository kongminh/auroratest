package aurora;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class AccountCacheImpl implements AccountCache {

  private final LinkedHashMapExtend<Long, Account> cacheMap;
  private Consumer<Account> accountListener;
  private List<Account> top3AccountsByBalance;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public AccountCacheImpl(int capacity) {
    this.cacheMap = new LinkedHashMapExtend<Long, Account>(capacity, 0.75f, true);
    top3AccountsByBalance = new ArrayList<>();
  }

  @Override
  public Account getAccountById(long id) {
    lock.writeLock().lock();
    try {
      Account account = cacheMap.get(id);
      if (account == null) {
        return null;
      }
      Account accountCopy = new Account(account.id, account.getBalance());
      return accountCopy;
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void subscribeForAccountUpdates(Consumer<Account> listener) {
    this.accountListener = listener;
  }

  @Override
  public List<Account> getTop3AccountsByBalance() {
    lock.readLock().lock();
    try {
      return Collections.unmodifiableList(top3AccountsByBalance);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public int getAccountByIdHitCount() {
    lock.readLock().lock();
    try {
      return cacheMap.getCountGetByIdHit();
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * Updates the top 3 accounts by balance based on changes to the cache.
   *
   * @param oldAccount the previous account associated with the ID, if any
   * @param newAccount the new account being added to the cache
   */
  private void updateTop3AccountsByBalance(Account oldAccount, Account newAccount) {
    // If the size of the top 3 accounts is less than 3, simply add the new account if it's not already in the list.
    if (top3AccountsByBalance.size() < 3) {
      if (oldAccount != null) {
        top3AccountsByBalance.remove(oldAccount);
      }
      top3AccountsByBalance.add(newAccount);
    } else {
      // If the size of the top 3 accounts is equal to 3, determine whether to replace one of them with the new account.
      if (oldAccount == null || !top3AccountsByBalance.contains(oldAccount)) {
        // If the old account is null (indicating an addition) or not found in the top 3, compare balances to determine replacement.
        if (newAccount.getBalance() > top3AccountsByBalance.get(top3AccountsByBalance.size() - 1).getBalance()) {
          // If the balance of the new account is greater than the smallest balance in the top 3, replace the smallest with the new account.
          top3AccountsByBalance.remove(top3AccountsByBalance.get(2));
          top3AccountsByBalance.add(newAccount);
        }
      } else {
        // If the old account is found in the top 3, compare balances to determine whether replacement or addition is needed.
        if (newAccount.getBalance() >= oldAccount.getBalance()
          || newAccount.getBalance() >= top3AccountsByBalance.get(top3AccountsByBalance.size() - 1).getBalance()
        ) {
          // If the balance of the new account is greater than or equal to the old account's balance or the smallest balance in the top 3, replace the old with the new account.
          top3AccountsByBalance.remove(oldAccount);
          top3AccountsByBalance.add(newAccount);
        } else {
          // If the balance of the new account is smaller than both the old account's balance and the smallest balance in the top 3, find a suitable replacement among other accounts in the cache.
          Account candidate = null;
          for (Account account : this.cacheMap.values()) {
            if (!top3AccountsByBalance.contains(account) && (candidate == null || account.getBalance() > candidate.getBalance())) {
              candidate = account;
            }
          }
          // If a suitable replacement is found, add it to the top 3 accounts list.
          if (candidate != null) {
            if (top3AccountsByBalance.size() == 3) {
              top3AccountsByBalance.remove(oldAccount);
            }
            top3AccountsByBalance.add(candidate);
          }
        }
      }
    }
    // Sort the top 3 accounts by balance in descending order.
    top3AccountsByBalance.sort(Comparator.comparingLong(Account::getBalance).reversed());
  }

  @Override
  public void putAccount(Account account) {
    lock.writeLock().lock();
    try {
      Account accountCopy = new Account(account.id, account.getBalance());
      Account oldAccount = cacheMap.put(accountCopy.id, accountCopy);
      if (oldAccount != null && !oldAccount.equals(accountCopy) && this.accountListener != null) {
        this.accountListener.accept(accountCopy);
      }
      this.updateTop3AccountsByBalance(oldAccount, accountCopy);
    } finally {
      lock.writeLock().unlock();
    }
  }
}
