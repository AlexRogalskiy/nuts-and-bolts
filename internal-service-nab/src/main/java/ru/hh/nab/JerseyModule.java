package ru.hh.nab;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.servlet.RequestScoped;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.spi.container.WebApplication;
import java.util.Map;

public class JerseyModule extends AbstractModule {
  private final WebApplication wa;
  private final JerseyModule.JerseyRequestScope REQUEST_SCOPE = new JerseyRequestScope();

  private static enum NullObject {
    INSTANCE
  }

  public JerseyModule(WebApplication wa) {
    this.wa = wa;
  }

  @Override
  protected void configure() {
    bind(StatsResource.class).in(Scopes.SINGLETON);
    bindScope(RequestScoped.class, REQUEST_SCOPE);
  }

  @Provides
  protected HttpContext httpContext() {
    return wa.getThreadLocalHttpContext();
  }

  private class JerseyRequestScope implements Scope {
    @Override
    public <T> Provider<T> scope(Key<T> key, final Provider<T> creator) {
      final String name = key.toString();
      return new Provider<T>() {
        public T get() {
          HttpContext context = wa.getThreadLocalHttpContext();
          synchronized (context) {
            Map<String, Object> props = context.getProperties();
            Object obj = props.get(name);
            if (NullObject.INSTANCE == obj) {
              return null;
            }
            @SuppressWarnings("unchecked")
            T t = (T) obj;
            if (t == null) {
              t = creator.get();
              props.put(name, (t != null) ? t : NullObject.INSTANCE);
            }
            return t;
          }
        }

        public String toString() {
          return String.format("%s[%s]", creator, REQUEST_SCOPE);
        }
      };
    }
  }
}
