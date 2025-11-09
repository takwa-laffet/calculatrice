package com.calculator.calculator.controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calc")
public class CalculatorController {

    @GetMapping("/add")
    public double add(@RequestParam double a, @RequestParam double b) {
        return a + b;
    }

    @GetMapping("/subtract")
    public double subtract(@RequestParam double a, @RequestParam double b) {
        return a - b;
    }

    @GetMapping("/multiply")
    public double multiply(@RequestParam double a, @RequestParam double b) {
        return a * b;
    }

    @GetMapping("/divide")
    public double divide(@RequestParam double a, @RequestParam double b) {
        if (b == 0) throw new IllegalArgumentException("Division par zéro impossible");
        return a / b;
    }

    @GetMapping("/power")
    public double power(@RequestParam double a, @RequestParam double b) {
        return Math.pow(a, b);
    }

    @GetMapping("/sqrt")
    public double sqrt(@RequestParam double a) {
        if (a < 0) throw new IllegalArgumentException("Nombre négatif impossible");
        return Math.sqrt(a);
    }

    @GetMapping("/log")
    public double log(@RequestParam double a) {
        if (a <= 0) throw new IllegalArgumentException("Nombre doit être > 0");
        return Math.log(a);  // log naturel
    }

    @GetMapping("/log10")
    public double log10(@RequestParam double a) {
        if (a <= 0) throw new IllegalArgumentException("Nombre doit être > 0");
        return Math.log10(a);
    }

    @GetMapping("/factorial")
    public long factorial(@RequestParam int n) {
        if (n < 0) throw new IllegalArgumentException("Nombre doit être >= 0");
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }

    @GetMapping("/sin")
    public double sin(@RequestParam double a) {
        return Math.sin(Math.toRadians(a));
    }

    @GetMapping("/cos")
    public double cos(@RequestParam double a) {
        return Math.cos(Math.toRadians(a));
    }

    @GetMapping("/tan")
    public double tan(@RequestParam double a) {
        return Math.tan(Math.toRadians(a));
    }

    @GetMapping("/asin")
    public double asin(@RequestParam double a) {
        return Math.toDegrees(Math.asin(a));
    }

    @GetMapping("/acos")
    public double acos(@RequestParam double a) {
        return Math.toDegrees(Math.acos(a));
    }

    @GetMapping("/atan")
    public double atan(@RequestParam double a) {
        return Math.toDegrees(Math.atan(a));
    }
}
