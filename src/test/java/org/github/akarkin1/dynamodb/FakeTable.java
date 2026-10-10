package org.github.akarkin1.dynamodb;

import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A Mockito mock of {@link DynamoDbTable} that answers {@code query}, {@code getItem},
 * {@code putItem} and {@code deleteItem} whichever overload the code under test uses, and records
 * each call normalized (query condition, key, put request). Every call and every page fetch also
 * records whether it ran inside a metrics timing ({@code timing}).
 */
public final class FakeTable<T> implements Answer<Object> {

  private final Class<T> beanClass;
  private final TableSchema<T> schema;
  private final BooleanSupplier timing;
  private final DynamoDbTable<T> table;

  private List<List<T>> pages = List.of();
  private final Map<Key, T> items = new HashMap<>();

  private final List<QueryConditional> queries = new ArrayList<>();
  private final List<Key> gets = new ArrayList<>();
  private final List<PutItemEnhancedRequest<T>> puts = new ArrayList<>();
  private final List<Key> deletes = new ArrayList<>();
  private final List<Boolean> timedCalls = new ArrayList<>();

  @SuppressWarnings("unchecked")
  private FakeTable(Class<T> beanClass, BooleanSupplier timing) {
    this.beanClass = beanClass;
    this.schema = TableSchema.fromBean(beanClass);
    this.timing = timing;
    this.table = Mockito.mock(DynamoDbTable.class, this);
  }

  public static <T> FakeTable<T> of(Class<T> beanClass, BooleanSupplier timing) {
    return new FakeTable<>(beanClass, timing);
  }

  public DynamoDbTable<T> table() {
    return table;
  }

  /** Every query returns these pages (lazily, as the Enhanced Client does). */
  @SafeVarargs
  public final FakeTable<T> withPages(List<T>... pages) {
    this.pages = List.of(pages);
    return this;
  }

  /** {@code getItem} returns this item for its key. */
  public FakeTable<T> withItem(T item) {
    items.put(keyOf(item), item);
    return this;
  }

  public List<QueryConditional> queries() {
    return queries;
  }

  public List<Key> gets() {
    return gets;
  }

  public List<PutItemEnhancedRequest<T>> puts() {
    return puts;
  }

  public List<Key> deletes() {
    return deletes;
  }

  /** One entry per DynamoDB call and per fetched query page: whether it ran inside a timing. */
  public List<Boolean> timedCalls() {
    return timedCalls;
  }

  public TableSchema<T> schema() {
    return schema;
  }

  /** The key condition of a query rendered against the bean schema: attribute name → value. */
  public Map<String, AttributeValue> keyCondition(QueryConditional conditional) {
    Expression expression = conditional.expression(schema, TableMetadata.primaryIndexName());
    Map<String, AttributeValue> condition = new HashMap<>();
    expression.expressionNames().forEach((placeholder, name) -> {
      String valuePlaceholder = placeholder.replaceFirst("^#", ":");
      condition.put(name, expression.expressionValues().get(valuePlaceholder));
    });
    return condition;
  }

  public static Key key(String pk, String sk) {
    return Key.builder().partitionValue(pk).sortValue(sk).build();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object answer(InvocationOnMock invocation) throws Throwable {
    Object arg = invocation.getArguments().length == 1 ? invocation.getArgument(0) : null;
    switch (invocation.getMethod().getName()) {
      case "query" -> {
        timedCalls.add(timing.getAsBoolean());
        queries.add(queryConditional(arg));
        return PageIterable.create(() -> pages.stream()
            .map(items -> {
              timedCalls.add(timing.getAsBoolean());
              return Page.create(items);
            })
            .iterator());
      }
      case "getItem" -> {
        timedCalls.add(timing.getAsBoolean());
        Key key = getKey(arg);
        gets.add(key);
        return items.get(normalize(key));
      }
      case "putItem" -> {
        timedCalls.add(timing.getAsBoolean());
        puts.add(putRequest(arg));
        return null;
      }
      case "deleteItem" -> {
        timedCalls.add(timing.getAsBoolean());
        deletes.add(deleteKey(arg));
        return null;
      }
      case "tableSchema" -> {
        return schema;
      }
      case "tableName" -> {
        return "vpnbot";
      }
      case "keyFrom" -> {
        return keyOf((T) arg);
      }
      default -> {
        if (invocation.getMethod().getDeclaringClass() == Object.class) {
          return Answers.RETURNS_DEFAULTS.answer(invocation);
        }
        throw new UnsupportedOperationException(
            "FakeTable does not support " + invocation.getMethod());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private QueryConditional queryConditional(Object arg) {
    if (arg instanceof QueryConditional conditional) {
      return conditional;
    }
    if (arg instanceof QueryEnhancedRequest request) {
      return request.queryConditional();
    }
    QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder();
    ((Consumer<QueryEnhancedRequest.Builder>) arg).accept(builder);
    return builder.build().queryConditional();
  }

  @SuppressWarnings("unchecked")
  private Key getKey(Object arg) {
    if (arg instanceof Key key) {
      return key;
    }
    if (arg instanceof GetItemEnhancedRequest request) {
      return request.key();
    }
    if (arg instanceof Consumer<?> consumer) {
      GetItemEnhancedRequest.Builder builder = GetItemEnhancedRequest.builder();
      ((Consumer<GetItemEnhancedRequest.Builder>) consumer).accept(builder);
      return builder.build().key();
    }
    return keyOf((T) arg);
  }

  @SuppressWarnings("unchecked")
  private PutItemEnhancedRequest<T> putRequest(Object arg) {
    if (arg instanceof PutItemEnhancedRequest<?> request) {
      return (PutItemEnhancedRequest<T>) request;
    }
    if (arg instanceof Consumer<?> consumer) {
      PutItemEnhancedRequest.Builder<T> builder = PutItemEnhancedRequest.builder(beanClass);
      ((Consumer<PutItemEnhancedRequest.Builder<T>>) consumer).accept(builder);
      return builder.build();
    }
    return PutItemEnhancedRequest.builder(beanClass).item((T) arg).build();
  }

  @SuppressWarnings("unchecked")
  private Key deleteKey(Object arg) {
    if (arg instanceof Key key) {
      return key;
    }
    if (arg instanceof DeleteItemEnhancedRequest request) {
      return request.key();
    }
    if (arg instanceof Consumer<?> consumer) {
      DeleteItemEnhancedRequest.Builder builder = DeleteItemEnhancedRequest.builder();
      ((Consumer<DeleteItemEnhancedRequest.Builder>) consumer).accept(builder);
      return builder.build().key();
    }
    return keyOf((T) arg);
  }

  private Key keyOf(T item) {
    Map<String, AttributeValue> map = schema.itemToMap(item, true);
    return Key.builder().partitionValue(map.get("pk")).sortValue(map.get("sk")).build();
  }

  /** Keys compare by their values, whichever way they were built. */
  private static Key normalize(Key key) {
    Optional<AttributeValue> sort = key.sortKeyValue();
    Key.Builder builder = Key.builder().partitionValue(key.partitionKeyValue());
    sort.ifPresent(builder::sortValue);
    return builder.build();
  }

}
