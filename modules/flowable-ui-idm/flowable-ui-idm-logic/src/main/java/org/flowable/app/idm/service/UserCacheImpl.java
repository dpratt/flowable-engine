/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.app.idm.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.flowable.app.idm.cache.UserCache;
import org.flowable.app.idm.model.UserInformation;
import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.api.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


/**
 * Cache containing User objects to prevent too much DB-traffic (users exist separately from the Flowable tables, they need to be fetched afterward one by one to join with those entities).
 * <p>
 * TODO: This could probably be made more efficient with bulk getting. The Google cache impl allows this: override loadAll and use getAll() to fetch multiple entities.
 *
 * @author Frederik Heremans
 * @author Joram Barrez
 */
@Service
public class UserCacheImpl implements UserCache {

  @Autowired
  protected Environment environment;

  @Autowired
  protected IdmIdentityService identityService;

  @Autowired
  protected UserService userService;

  protected LoadingCache<String, CachedUser> userCache;

  @PostConstruct
  protected void initCache() {
    Long userCacheMaxSize = environment.getProperty("cache.users.max.size", Long.class);
    Long userCacheMaxAge = environment.getProperty("cache.users.max.age", Long.class);

    userCache = Caffeine.newBuilder().maximumSize(userCacheMaxSize != null ? userCacheMaxSize : 2048)
        .expireAfterAccess(userCacheMaxAge != null ? userCacheMaxAge : (24 * 60 * 60), TimeUnit.SECONDS)
        .recordStats()
        .build(userId -> {
          User userFromDatabase = null;
          if (!environment.getProperty("ldap.enabled", Boolean.class, false)) {
            userFromDatabase = identityService.createUserQuery().userIdIgnoreCase(userId.toLowerCase()).singleResult();
          } else {
            userFromDatabase = identityService.createUserQuery().userId(userId).singleResult();
          }

          if (userFromDatabase == null) {
            throw new UsernameNotFoundException("User " + userId + " was not found in the database");
          }

          Collection<GrantedAuthority> grantedAuthorities = new ArrayList<GrantedAuthority>();
          UserInformation userInformation = userService.getUserInformation(userFromDatabase.getId());
          for (String privilege : userInformation.getPrivileges()) {
            grantedAuthorities.add(new SimpleGrantedAuthority(privilege));
          }

          return new CachedUser(userFromDatabase, grantedAuthorities);
        });
  }

  public void putUser(String userId, CachedUser cachedUser) {
    userCache.put(userId, cachedUser);
  }

  public CachedUser getUser(String userId) {
    return getUser(userId, false, false, true); // always check validity by default
  }

  public CachedUser getUser(String userId, boolean throwExceptionOnNotFound, boolean throwExceptionOnInactive, boolean checkValidity) {
      // The cache is a LoadingCache and will fetch the value itself
    try {
      return userCache.get(userId);
    } catch (UsernameNotFoundException e) {
      if(throwExceptionOnNotFound) {
        throw e;
      } else {
        return null;
      }
    } catch (LockedException e) {
      if (throwExceptionOnNotFound) {
        throw e;
      } else {
        return null;
      }
    }
  }

  @Override
  public void invalidate(String userId) {
    userCache.invalidate(userId);
  }
}
