package brooklyn.util.internal;

import java.util.Date
import java.util.Set

import org.junit.Assert
import org.junit.Test

import brooklyn.util.internal.LanguageUtils.FieldVisitor


class LanguageUtilsTest {
    @Test
	public void testSetFieldsFromMap() {
		A a = []
		Map unused = LanguageUtils.setFieldsFromMap(a, [num:1,mun:2])
		Assert.assertEquals(1, a.num);
		Assert.assertEquals([mun:2], unused)
	}
	
    @Test
	public void testVisitingFieldsDeepNonLooping() {
		BigUn b2 = new BigUn(name:"L'il Guy", num:10, dates:[ new Date() ])
//		b2.dates = [ new Date() ] as Date[]
		BigUn b1 = new BigUn(name:"Big Guy", num:40)
		b1.child = b2;
		b1.children += b2
		b2.child = b1
		
		int sum = 0;
        FieldVisitor numSummer = { parent, name, value -> if ("num"==name) sum+=value } as FieldVisitor
		LanguageUtils.visitFields(b1, numSummer)
		
		Assert.assertEquals(50, sum) 
	}
	
    private static class A {
        int num;
    }
    
    private static class BigUn {
        String name;
        int num;
        BigUn child;
        Set children = []
        Date[] dates;
    }

	//test the default getter, and equals
	static class TestingFieldA {
		public int a = 6;
		int getAt(A aa) { return aa.num * a; }
		static A aa = [num:10];
		int x = -1;
	}
	static class TestingFields extends TestingFieldA {
		int b = 7;
		int getB() { -7 }
		public int c = 8;
		int getD() { 9 }
	}
	@Test
	public void testSomeGet() {
		TestingFields tf = []
		Assert.assertEquals( [6, -7, 7, 8, 9, 60],
			["a", "b", "@b", "c", "d", TestingFields.aa].collect {
				LanguageUtils.DEFAULT_FIELD_GETTER.call(tf, it)
			})
	}
	
	@Test
	public void testEquals() {
		//basic
		TestingFields t1 = [], t2 = []
		Assert.assertTrue LanguageUtils.equals(t1, t2, null, ["a", "b"])
		Assert.assertTrue LanguageUtils.equals(t1, t2, TestingFields, ["a", "b"])
		Assert.assertFalse LanguageUtils.equals(t1, t2, String, ["a", "b"])
		Assert.assertFalse LanguageUtils.equals(t1, t2, null, ["z"])
		Assert.assertTrue LanguageUtils.equals(t1, t2, null, (["a", "b"] as String[]))
		
		//type hierarchy
		TestingFieldA t1a = []
		Assert.assertTrue LanguageUtils.equals(t1, t1a, null, "a")
		Assert.assertTrue LanguageUtils.equals(t1, t1a, TestingFieldA, "a")
		Assert.assertFalse LanguageUtils.equals(t1, t1a, TestingFields, "a")
		Assert.assertFalse LanguageUtils.equals(t1, t1a, null, "a", "b")
		t1.b = 0
		Assert.assertTrue LanguageUtils.equals(t1, t1a, null, "a")
		t1a.a = -6
		Assert.assertFalse LanguageUtils.equals(t1, t1a, null, "a")
		
		//direct access to field
		Assert.assertTrue LanguageUtils.equals(t1, t2, null, "b")
		Assert.assertFalse LanguageUtils.equals(t1, t2, null, "@b")
		Assert.assertTrue LanguageUtils.equals(t1, t2, null, "@a")
		
		//and complex field
		Assert.assertTrue LanguageUtils.equals(t1, t2, null, TestingFields.aa)
		//because we changed t1a.a, and getAt(A) refers to int a
		Assert.assertFalse LanguageUtils.equals(t1, t1a, null, TestingFields.aa)
		
		//test it works with POJO objects (non-groovy)
		Assert.assertTrue LanguageUtils.equals("hi", "ho", null, "count")
		Assert.assertFalse LanguageUtils.equals("hi", "hello", null, "count")
		
		//and a tricky one, because x is a groovy property, it is _private_ so we cannot see it as a field wrt t1
		Assert.assertFalse LanguageUtils.equals(t1, t1a, null, "@x")
		//but in the context of t1a we can.. in short, be careful with fields
		Assert.assertTrue LanguageUtils.equals(t1a, t1a, null, "@x")
	}

	@Test
	public void testHashCode() {
		//basic
		TestingFields t1 = [], t2 = []
		Assert.assertTrue LanguageUtils.hashCode(t1, ["a", "b"]) == LanguageUtils.hashCode(t2, ["a", "b"])
		Assert.assertTrue LanguageUtils.hashCode(t1, ["a", "@b"]) == LanguageUtils.hashCode(t2, ["a", "@b"])
		Assert.assertFalse LanguageUtils.hashCode(t1, ["a", "b"]) == LanguageUtils.hashCode(t2, ["a", "@b"])
		t2.b = 0;
		Assert.assertTrue LanguageUtils.hashCode(t1, ["a", "b"]) == LanguageUtils.hashCode(t2, ["a", "b"])
		Assert.assertTrue LanguageUtils.hashCode(t1, ["a", "@b"]) == LanguageUtils.hashCode(t2, ["a", "@b"])
		Assert.assertEquals 0, LanguageUtils.hashCode(null, ["a", "@b"])
	}
	
}
