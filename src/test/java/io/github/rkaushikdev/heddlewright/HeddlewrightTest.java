package io.github.rkaushikdev.heddlewright;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.tools.Sources;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HeddlewrightTest {

    private static boolean hasMethod(String namespace, String method) {
        return Stream.of(Heddlewright.namespace(namespace).orElseThrow().getMethods())
                .anyMatch(found -> found.getName().equals(method) && Modifier.isStatic(found.getModifiers()));
    }

    private static List<Path> sourceFiles() {
        return Sources.walk(Sources.mainSources(), List.of(".java"));
    }

    private static String key(Path file) {
        return Sources.name(Sources.mainSources(), file);
    }

    private static String className(Path file) {
        String name = key(file);
        return "io.github.rkaushikdev.heddlewright."
                + name.substring(0, name.length() - 5).replace('/', '.');
    }

    @Nested
    class ThePublicSurface {

        @Test
        void exportsANamespacePerAreaRatherThanAFlatPile() {
            for (Heddlewright.Namespace entry : Heddlewright.NAMESPACES) {
                assertThat(Modifier.isPublic(entry.type().getModifiers())).isTrue();
                assertThat(Stream.of(entry.type().getMethods())
                                .filter(method -> method.getDeclaringClass() == entry.type())
                                .count())
                        .as(entry.name())
                        .isGreaterThan(0);
            }
        }

        @Test
        void carriesAVersion() {
            assertThat(Heddlewright.VERSION).matches("\\d+\\.\\d+\\.\\d+");
        }

        @Test
        void reachesTheDraftLayer() {
            assertThat(hasMethod("draft", "create")).isTrue();
            assertThat(hasMethod("drawdown", "drawdownFromDraft")).isTrue();
        }

        @Test
        void reachesTheJacquardLayer() {
            assertThat(hasMethod("deck", "deckFromLiftplan")).isTrue();
            assertThat(hasMethod("machine", "listMachines")).isTrue();
        }

        @Test
        void reachesTheShopFloor() {
            assertThat(hasMethod("schedule", "schedule")).isTrue();
            assertThat(hasMethod("orders", "createOrder")).isTrue();
        }

        @Test
        void namesNoNamespaceTwice() {
            List<String> names = Heddlewright.NAMESPACES.stream()
                    .map(Heddlewright.Namespace::name)
                    .toList();
            assertThat(new HashSet<>(names)).hasSameSizeAs(names);
        }
    }

    @Nested
    class EverySourceFile {

        private final List<Path> files = sourceFiles();

        @Test
        void findsAGoodManyOfThem() {
            assertThat(files.size()).isGreaterThan(40);
        }

        @Test
        void exportsSomething() throws ClassNotFoundException {
            for (Path file : files) {
                Class<?> type = Class.forName(className(file));
                if (!Modifier.isPublic(type.getModifiers())) {
                    continue;
                }
                List<Member> members = new ArrayList<>();
                members.addAll(List.of(type.getDeclaredMethods()));
                members.addAll(List.of(type.getDeclaredFields()));
                members.addAll(List.of(type.getDeclaredConstructors()));
                boolean exported = members.stream().anyMatch(member -> Modifier.isPublic(member.getModifiers()))
                        || Stream.of(type.getDeclaredClasses())
                                .anyMatch(inner -> Modifier.isPublic(inner.getModifiers()));
                assertThat(exported).as(key(file) + " exports nothing").isTrue();
            }
        }

        @Test
        void isNamedInTheBarrelExceptTheOnesThatAreWiring() {
            // The barrel's own source is read rather than its lists. Two areas can hold classes of
            // the same simple name, so a check on simple names alone passes for a class nobody
            // listed as long as another class happens to share its name, which is exactly the case
            // this has to catch. The import names the package as well.
            String barrel = Sources.read(Sources.mainSources().resolve("Heddlewright.java"));
            for (Path file : files) {
                String name = key(file);
                boolean wiring = name.equals("Heddlewright.java")
                        || name.startsWith("cli/")
                        || name.startsWith("tools/")
                        || name.matches("service/(Draft|Cloth|Floor|Weave)Routes\\.java|service/Server\\.java");
                if (wiring || !Modifier.isPublic(classOf(file).getModifiers())) {
                    continue;
                }
                String simple = name.substring(name.lastIndexOf('/') + 1, name.length() - 5);
                assertThat(barrel)
                        .as(name + " is not named in the barrel")
                        .contains("import " + className(file) + ";")
                        .contains(simple + ".class");
            }
        }

        @Test
        void namesNothingInTheBarrelThatIsNotASourceFile() {
            String barrel = Sources.read(Sources.mainSources().resolve("Heddlewright.java"));
            Set<String> present = new HashSet<>();
            for (Path file : files) {
                present.add(className(file));
            }
            Matcher named = Pattern.compile("(?m)^import (io\\.github\\.rkaushikdev\\.heddlewright\\.[\\w.]+);")
                    .matcher(barrel);
            int count = 0;
            while (named.find()) {
                count += 1;
                assertThat(present)
                        .as("the barrel names " + named.group(1) + ", which is not there")
                        .contains(named.group(1));
            }
            assertThat(count).isGreaterThan(40);
        }

        private Class<?> classOf(Path file) {
            try {
                return Class.forName(className(file));
            } catch (ClassNotFoundException err) {
                throw new AssertionError(err);
            }
        }
    }
}
