package com.hmdp.entity;

import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        String s="Baidu";
        Set<Character> set = new HashSet<>();
        char[] chars = s.toCharArray();

        Scanner input = new Scanner(System.in);
        int count = input.nextInt();

        for (int i = 0; i < count; i++) {
            for (char c1 : chars) {
                set.add(c1);
            }

            String message = input.next();
            char[] charArray = message.toCharArray();

            for (char c2 : charArray) {
                if (!set.add(c2)) {
                    set.remove(c2);
                }
            }
            if (set.isEmpty()) {
                System.out.println("Yes");
            } else {
                System.out.println("No");
            }
            set.clear();
        }
    }
}
