package com.nio.test.app;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Author: xudacheng@patsnap.com
 * Date:   10/22/18 3:02 PM
 */

public class DefaultAttributeMapTest {

    @BeforeClass
    public static void beforeClass() {
        System.out.println("before class..");
    }

    @Before
    public void before() {
        System.out.println("before");
    }

    @Test
    public void test01() {
        DefaultAttributeMap attributeMap = new DefaultAttributeMap();
        
        AttributeKey<String> key1 = AttributeKey.valueOf("hello_1");
        Assert.assertFalse(attributeMap.hasAttr(key1));

        AttributeKey<Long> key2 =  AttributeKey.valueOf("hello_2");
        Assert.assertFalse(attributeMap.hasAttr(key2));

        AttributeKey<Integer> key3 =  AttributeKey.valueOf("hello_3");
        Assert.assertFalse(attributeMap.hasAttr(key3));

        Attribute<String> attr1 = attributeMap.attr(key1);
        Assert.assertEquals(attr1.key(), key1);
        attr1.set("word_1");
        Assert.assertEquals(attr1.get(), "word_1");

        Attribute<Long> attr2 = attributeMap.attr(key2);
        Assert.assertEquals(attr2.key(), key2);
        attr2.set(1L);
        Assert.assertTrue(attr2.get() == 1L);

        Attribute<Integer> attr3 = attributeMap.attr(key3);
        Assert.assertEquals(attr3.key(), key3);
        attr3.set(1);
        Assert.assertTrue(attr3.get() == 1);
    }

    @Test
    public void test02() {
        DefaultAttributeMap attributeMap = new DefaultAttributeMap();

        for (int i = 1; i < 10; i++) {
            AttributeKey<String> key1 = AttributeKey.valueOf(String.format("hello_%d", i));
            Assert.assertFalse(attributeMap.hasAttr(key1));


            Attribute<String> attr1 = attributeMap.attr(key1);
            Assert.assertEquals(attr1.key(), key1);
            attr1.set(String.format("word_%d", i));
            Assert.assertEquals(attr1.get(), String.format("word_%d", i));
        }

        System.out.println(attributeMap.toString());
    }

    @After
    public void after() {
        System.out.println("after");
    }

    @AfterClass
    public static void afterClass() {
        System.out.println("after class..");
    }
}

