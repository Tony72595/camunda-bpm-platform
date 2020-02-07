/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.dmn.feel.impl.integration.spin;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static scala.jdk.CollectionConverters.ListHasAsScala;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.camunda.feel.impl.spi.JavaCustomValueMapper;
import org.camunda.feel.interpreter.impl.Context.StaticContext;
import org.camunda.feel.interpreter.impl.Val;
import org.camunda.feel.interpreter.impl.ValContext;
import org.camunda.feel.interpreter.impl.ValList;
import org.camunda.feel.interpreter.impl.ValNull$;spinXmlToVal
import org.camunda.feel.interpreter.impl.ValString;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.xml.SpinXmlAttribute;
import org.camunda.spin.xml.SpinXmlElement;
import org.camunda.spin.xml.SpinXmlNode;
import scala.Tuple2;
import scala.collection.immutable.Map$;
import scala.collection.immutable.Seq;


public class CamundaSpinValueMapper extends JavaCustomValueMapper {

  @Override
  public Optional<Val> toValue(Object x, Function<Object, Val> innerValueMapper) {
    if (x instanceof SpinJsonNode) {
      SpinJsonNode node = (SpinJsonNode) x;
      return Optional.of(this.spinJsonToVal(node, innerValueMapper));

    } else if (x instanceof SpinXmlElement) {
      SpinXmlElement element = (SpinXmlElement) x;
      return Optional.of(this.spinXmlToVal(element));

    } else {
      return Optional.empty();

    }
  }

  @Override
  public Optional<Object> unpackValue(Val value, Function<Val, Object> innerValueMapper) {
    return Optional.empty();
  }

  @Override
  public int priority() {
    return 30;
  }

  protected Val spinJsonToVal(SpinJsonNode node, Function<Object, Val> innerValueMapper) {
    if (node.isObject()) {
      Map pairs = node.fieldNames().stream()
        .collect(toMap(field -> field, field -> spinJsonToVal(node.prop(field), innerValueMapper)));
      return new ValContext(new StaticContext(toScalaImmutableMap(pairs), null));

    } else if (node.isArray()) {
      java.util.List<Val> values = node.elements().stream().map(e -> spinJsonToVal(e, innerValueMapper)).collect(toList());
      return new ValList(ListHasAsScala(values).asScala().toList());

    } else if (node.isNull()) {
       return new ValNull$();
    } else {
      return innerValueMapper.apply(node.value());

    }
  }

  protected Val spinXmlToVal(SpinXmlElement element) {
    String name = nodeName(element);
    Val value = spinXmlElementToVal(element);
    scala.collection.immutable.Map map = new scala.collection.immutable.Map.Map1(name, value);

    return new ValContext(new StaticContext(map, null));
  }

  protected Val spinXmlElementToVal(final SpinXmlElement e) {
    Map<String, Object> membersMap = new HashMap();

    String content = e.textContent().trim();
    if (!content.isEmpty()) {
      membersMap.put("$content", new ValString(content));
    }

    Map<String, ValString> attributes = e.attrs().stream().collect(toMap(attr -> spinXmlAttributeToKey(attr), attr -> new ValString(attr.value())));
    membersMap.putAll(attributes);

    Map<String, Val> childrenMap = e.childElements().stream()
     .collect(
       groupingBy(
         el -> nodeName(el),
         mapping(el -> spinXmlElementToVal(el), toList())
                 ))
     .entrySet().stream()
       .collect(toMap(entry -> entry.getKey(), entry -> {
         scala.collection.immutable.List vals = ListHasAsScala(entry.getValue()).asScala().toList();
         if (!vals.isEmpty()) {
           return new ValList(vals);
         } else {
           return new ValNull$();
         }
       }));
    membersMap.putAll(childrenMap);

    if (membersMap.isEmpty()) {
      return new ValNull$();
    } else {
      return new ValContext(new StaticContext(toScalaImmutableMap(membersMap), null));
    }
  }

  protected String spinXmlAttributeToKey(SpinXmlAttribute attribute) {
    return "@" + nodeName(attribute);
  }

  protected String nodeName(SpinXmlNode n) {
    return Optional.of(n.prefix())
                   .map(p -> p + "$" + n.name())
                   .orElse(n.name());
  }

  protected <K, V> scala.collection.immutable.Map<K, V> toScalaImmutableMap(Map<K, V> javaMap) {
    List<Tuple2<K, V>> tuples = javaMap.entrySet()
                                    .stream()
                                    .map(e -> Tuple2.apply(e.getKey(), e.getValue()))
                                    .collect(toList());

    Seq<Tuple2<K, V>> scalaSeq = ListHasAsScala(tuples).asScala().toSeq();

    return Map$.MODULE$.apply(scalaSeq);
  }
}

