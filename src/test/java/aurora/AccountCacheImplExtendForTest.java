package aurora;

import java.util.Collection;

public class AccountCacheImplExtendForTest extends AccountCacheImpl {

  public AccountCacheImplExtendForTest(int capacity) {
    super(capacity);
  }

  public Collection<Account> getAllAccounts() {
    lock.readLock().lock();
    try {
    return this.cacheMap.values();
    } finally {
      this.lock.readLock().unlock();
    }
  }
}
