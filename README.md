# Glam

Gnarwhal's Lambda Calculus Interpreter

## About

A simple lambda calculus playground  
Either use the interactive shell or execute a script file

### The shell

The interactive shell is so bad :(

Enter any valid expression and it will attempt to beta-reduce it. Or rather, I hope it beta-reduces it. I don't have any formal training, so I could have
missed some gotchas when implementing reduction.

Shell commands are prefixed by a `\`  
Current shell commands include:
- `\q`, `\quit`, `\exit` -> Exit the shell
- `\exec [script]` -> Execute a script file
- `\help` -> List shell commands

### The syntax

The syntax used tries to be faithful to true lambda calculus.
There are two main modifications.
1. The `λ` symbol is not easy to type on any mainstream keyboard layouts that I'm familiar with, so `>` has been added as an alternative. `λ` is still accepted however. 
2. Variable names are not exclusively single symbols; they are strings of symbols. As such all variables must be whitespace separated. 

Variable names can be any sequence of symbols that don't include the reserved symbols `λ`, `>`, `.`, `(`, `)`, and whitespace characters.

Lambda functions can only take a single input, but nested lambdas can be syntactically shortened to what look like multi-parameter lambdas. See examples below. 

#### Examples

Variable syntax  
`var`  

Lambda syntax  
`>var.expr`  

Nested lambda syntax  
`>x.>y.>z.expr`  
`>x y z.expr`  

Ordering with parentheses  
`(>x y.z) w`

### What is the Lambda Calculus?

Nope. Nope nope nope nope nope.
This is usually the part of the README.md where I try and give a bit of insight into the inspiration behind a project. But this time?
I'm not even gonna try and explain it. I'll say one thing about it and then you're on your own.
The Lambda Calculus is a formal system of mathematics that can be used to perform any computation. Yes that is exactly like turing machines. They are equivalent. 


### Obligatory future plans section

- Comments
- Naming of expressions
- Show beta-reduction steps
- Multiline expressions

### Etymology

Naming is hard

`Gnarwhal + Lambda`

The irony is that Glam is quite far from glamorous. It is most kludgy.
I suppose maybe that's something to work for. Have Glam live up to it's name. Or just change it's name because it's bad.

