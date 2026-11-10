package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Component;
import com.ll.standard.util.Ut;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스프링 ApplicationContext 를 흉내 낸 IoC 컨테이너 v2.
 * basePackage 아래에서 @Component(및 @Component 가 붙은 @Service·@Repository 등)가 붙은 클래스를
 * reflections 로 스캔해 빈으로 등록하고, 생성자 파라미터 타입으로 의존 빈을 찾아 주입한다.
 */
public class ApplicationContext {
    private final String basePackage;

    /** 빈 이름 → 빈 클래스 (빈 생성 정보) */
    private final Map<String, Class<?>> beanClasses = new LinkedHashMap<>();

    /** 빈 이름 → 만들어진 빈 (싱글톤 보관소) */
    private final Map<String, Object> beans = new HashMap<>();

    /** 지금 만드는 중인 빈 이름들. 순환 의존을 잡아내는 데 쓴다. */
    private final Set<String> beansInCreation = new LinkedHashSet<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        scanComponents();

        // 스프링처럼 싱글톤 빈은 컨테이너 초기화 때 미리 만들어 둔다.
        beanClasses.keySet().forEach(this::genBean);
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        Object bean = beans.get(beanName);

        if (bean == null) {
            bean = createBean(beanName);
            beans.put(beanName, bean);
        }

        return (T) bean;
    }

    private void scanComponents() {
        Reflections reflections = new Reflections(basePackage);

        reflections.getTypesAnnotatedWith(Component.class).stream()
                .filter(this::isInstantiableClass)
                .forEach(cls -> beanClasses.put(toBeanName(cls), cls));
    }

    /** @Service 같은 애너테이션 자체나 인터페이스·추상 클래스는 빈으로 만들 수 없으니 거른다. */
    private boolean isInstantiableClass(Class<?> cls) {
        return !cls.isAnnotation() && !cls.isInterface() && !Modifier.isAbstract(cls.getModifiers());
    }

    private String toBeanName(Class<?> cls) {
        return Ut.str.lcfirst(cls.getSimpleName());
    }

    private Object createBean(String beanName) {
        Class<?> beanClass = beanClasses.get(beanName);

        if (beanClass == null) {
            throw new IllegalArgumentException("등록되지 않은 빈입니다: " + beanName);
        }

        if (!beansInCreation.add(beanName)) {
            throw new IllegalStateException("순환 의존이 있습니다: " + beansInCreation + " -> " + beanName);
        }

        try {
            Constructor<?> constructor = selectConstructor(beanClass);
            Object[] args = resolveDependencies(constructor.getParameters());

            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("빈 생성 실패: " + beanName, e);
        } finally {
            beansInCreation.remove(beanName);
        }
    }

    /** 생성자가 여러 개면 파라미터가 가장 많은 생성자로 주입한다. */
    private Constructor<?> selectConstructor(Class<?> beanClass) {
        return Arrays.stream(beanClass.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
    }

    private Object[] resolveDependencies(Parameter[] parameters) {
        return Arrays.stream(parameters)
                .map(parameter -> genBean(findBeanName(parameter)))
                .toArray();
    }

    /** 파라미터 타입에 맞는 빈 이름을 찾는다. 후보가 여럿이면 파라미터 이름과 같은 빈을 고른다. */
    private String findBeanName(Parameter parameter) {
        List<String> candidates = beanClasses.entrySet().stream()
                .filter(entry -> parameter.getType().isAssignableFrom(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        if (candidates.size() == 1) return candidates.getFirst();

        if (parameter.isNamePresent() && candidates.contains(parameter.getName())) {
            return parameter.getName();
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("주입할 빈이 없습니다: " + parameter.getType().getName());
        }

        throw new IllegalStateException("주입할 빈이 여러 개입니다: " + parameter.getType().getName() + " " + candidates);
    }
}
