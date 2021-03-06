/*
* Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.verification.tools.integrity.schema;

import com.google.common.collect.Sets;
import grakn.common.util.Pair;
import grakn.verification.tools.integrity.IntegrityException;
import grakn.verification.tools.integrity.SemanticSet;
import grakn.verification.tools.integrity.Type;
import grakn.verification.tools.integrity.Validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transitive closure of Sub, not including (x,x) pairs
 * In other words, transitive Sub relation without the identity relation
 */
public class TransitiveSub implements SemanticSet<Pair<Type, Type>> {

    private Set<Pair<Type,Type>> set;

    public TransitiveSub() {
        set = new HashSet<>();
    }

    public boolean contains(Pair<Type, Type> item) {
        return set.contains(item);
    }

    @Override
    public void validate() {
        /*
        Conditions of validity:
        1. every type that isn't a meta type has exactly one parent in [entity, relation or attribute]
        2. (x,x) is not in the transitive closure, as this would mean there is a loop
        3. every type has an entry (x, thing)
         */

        Set<String> metaTypesWithoutMetaThing = Sets.newHashSet(
                Validator.META_TYPES.ENTITY.getName(),
                Validator.META_TYPES.RELATION.getName(),
                Validator.META_TYPES.ATTRIBUTE.getName(),
                "role"
        );

        // condition 1:
        Map<Type, Integer> typeMetaParentCount = new HashMap<>();
        for (Pair<Type, Type> item : set) {
            Type child = item.first();
            Type parent = item.second();
            if (metaTypesWithoutMetaThing.contains(parent.label())) {
                typeMetaParentCount.putIfAbsent(child, 0);
                typeMetaParentCount.compute(child, (childType, oldCount) -> oldCount + 1);
            }
        }

        Set<Type> nonMetaTypes = set.stream()
                .map(pair -> pair.first())
                .filter(type -> !metaTypesWithoutMetaThing.contains(type.label()))
                .filter(type -> !type.label().equals("thing"))
                .collect(Collectors.toSet());
        for (Type type : nonMetaTypes) {
            int numMetaParents = typeMetaParentCount.getOrDefault(type, 0);
            if (numMetaParents != 1) {
                throw IntegrityException.typeDoesNotHaveExactlyOneMetaSupertype(type, numMetaParents);
            }
        }

        // condition 2: (x,x) not in the transitive closure
        for (Pair<Type, Type> sub : set) {
            if (sub.first().equals(sub.second())) {
                throw IntegrityException.subHierarchyHasLoop(sub.first());
            }
        }

        // condition 3:
        Set<Type> children = set.stream().map(pair -> pair.first()).collect(Collectors.toSet());
        for (Type child : children) {
            boolean hasThingSuper = false;
            for (Pair<Type, Type> sub : set) {
                if (sub.first() == child && sub.second().label().equals(Validator.META_TYPES.THING.getName())) {
                    hasThingSuper = true;
                    break;
                }
            }
            if (!hasThingSuper) {
                throw IntegrityException.typeDoesNotHaveThingSuperType(child);
            }
        }


    }

    @Override
    public void add(Pair<Type, Type> item) {
        set.add(item);
    }

    @Override
    public Iterator<Pair<Type, Type>> iterator() {
        return set.iterator();
    }

    public TransitiveSub shallowCopy() {
        TransitiveSub copy = new TransitiveSub();
        set.forEach(copy::add);
        return copy;
    }

    public int size() {
        return set.size();
    }
}
