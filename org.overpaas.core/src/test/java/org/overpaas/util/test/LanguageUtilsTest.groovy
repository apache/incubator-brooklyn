package org.overpaas.util.test;

import org.junit.Assert
import org.junit.Test
import org.overpaas.util.LanguageUtils
import org.overpaas.util.LanguageUtils.FieldVisitor

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
}
