import platform.posix.*
import kotlinx.cinterop.*

class Environment {

	constructor() {}

	private class Binding {
		var stack = ArrayList<Int>()
		var next  = 1

		init {
			stack.add(0)
		}

		fun step(): Int {
			stack.add(next)
			next += 1
			return current()
		}

		fun current() = stack.get(stack.lastIndex)

		fun drop() {
			stack.removeLast()
		}
	}

	private val env = HashMap<String, Binding>()

	fun register(name: String): Int {
		return if (env[name] == null) {
			env[name] = Binding()
			env[name]!!.current()
		} else {
			env[name]!!.step()
		}
	}

	fun unregister(name: String) {
		env[name]!!.drop()
	}

	fun check(name: String) =
		if (env[name] == null) {
			-1
		} else {
			env[name]!!.current()
		}
}

class Parser(var src: String, var index: Int, var env: Environment = Environment()) {

	fun go(vararg options: (Parser) -> Result): Result {
		var result: Result = Err("Dummy", 0)
		for (option in options) {
			result = option(this)
			when (result) {
				is Success -> break
				is Err     -> if (result.fatal) { break }
			}
		}
		return result
	}

	fun clone(): Parser {
		return Parser(this.src, this.index, this.env)
	}

	fun merge(parser: Parser): Parser {
		this.src   = parser.src
		this.index = parser.index
		this.env   = parser.env
		return this
	}

	fun trim(pred: (Char) -> Boolean): Parser {
		collect(pred)
		return this
	}

	fun collect(pred: (Char) -> Boolean): String {
		if (src.length == 0) { return "" }
		var index = 0
		while (index < this.src.length && pred(this.src[index])) {
			index += 1
		}
		var substr = this.src.substring(0, index)
		this.src = this.src.substring(index)
		this.index += index
		return substr
	}
	
	fun advance(count: Int): Parser {
		this.src    = this.src.substring(count)
		this.index += count
		return this
	}

	operator fun get(index: Int) =
		if (index < src.length) {
			src[index]
		} else {
			0.toChar()
		}
}

sealed class Result() {}
class Success(val parser: Parser, val expr: Expression) : Result() {}
class Err(val message: String, val index: Int, val fatal: Boolean = false) : Result() {}

sealed class Expression {
	abstract fun beta_normal(): Expression

	abstract fun apply(lambda: Lambda): Expression

	companion object {
		fun is_reserved(c: Char) =
			c == '>' ||
			c == '.' ||
			c == '(' ||
			c == ')' ||
			c == 'λ'

		fun is_var_char(c: Char) =
			!is_reserved(c) &&
			!c.isWhitespace()

		fun parse(src: String): Result {
			var result = Parser(src, 0).go(Application::parse)
			return when (result) {
				is Success -> {
					if (result.parser.index != src.length) {
						Err("Invalid character", result.parser.index)
					} else {
						result
					}
				}
				is Err -> {
					result
				}
			}
		}
	}
}

class Application(val subexprs: List<Expression>) : Expression() {
	override fun beta_normal(): Expression {
		var normals = this.subexprs.map { expr -> expr.beta_normal() }.toMutableList()
		while (normals.get(0) is Lambda && normals.size > 1) {
			normals.set(0, (normals.get(0) as Lambda).apply_to(normals.get(1)).beta_normal())
			normals.removeAt(1)
		}
		return if (normals.size == 1) {
			normals[0]
		} else {
			Application(normals)
		}
	}

	override fun apply(lambda: Lambda): Expression = Application(subexprs.map { expr -> expr.apply(lambda) })

	companion object {
		fun parse(parser: Parser): Result {
			var local_parser = parser.clone().trim { it.isWhitespace() }
			val exprs  = ArrayList<Expression>()
			while (local_parser.src.length > 0) {
				var result = local_parser.go(
					Variable::parse,
					Lambda  ::parse,
					Ordering::parse
				)
				when (result) {
					is Success -> {
						exprs.add(result.expr)
						local_parser.merge(result.parser)
					}
					is Err ->
						if (result.fatal) {
							return result
						} else {
							break
						}
				}
				local_parser.trim { it.isWhitespace() }
			}
			if (exprs.size == 0) {
				return Err("Not the beginning of an expression", local_parser.index)
			} else if (exprs.size == 1) {
				return Success(local_parser, exprs[0])
			} else {
				return Success(local_parser, Application(exprs))
			}
		}
	}

	override fun toString() = subexprs
		.map { expr ->
			if (expr is Lambda) {
				"($expr)"
			} else {
				"$expr"
			}
		}
		.joinToString(" ")
}

class Variable(val name: String, val id: Int) : Expression() {

	override fun beta_normal(): Expression = this

	override fun apply(lambda: Lambda) =
		if (lambda.param == this) {
			lambda.expr
		} else {
			this
		}

	companion object {
		fun parse(parser: Parser): Result {
			var local_parser = parser.clone()
			if (Expression.is_reserved(local_parser.src[0])) {
				return Err("Expected a Variable expression but none was found", local_parser.index)
			}
			var name = local_parser.collect(Expression::is_var_char)
			return Success(local_parser, Variable(name, local_parser.env.check(name)))
		}
	}

	override fun toString() = name

	override fun equals(other: Any?): Boolean =
		other is Variable &&
		name == other.name &&
		id   == other.id
}

class Lambda(val param: Variable, val expr: Expression) : Expression() {
	override fun beta_normal(): Expression = Lambda(param, expr.beta_normal())

	override fun apply(lambda: Lambda): Expression = Lambda(param, expr.apply(lambda))

	fun apply_to(arg: Expression): Expression = expr.apply(Lambda(param, arg))

	companion object {
		fun parse(parser: Parser): Result {
			var local_parser = parser.clone()
			if (local_parser[0] != '>' && local_parser[0] != 'λ') {
				return Err("Expected a Lambda expression but none was found", parser.index)
			}
			local_parser.advance(1)
			return subparse(local_parser)
		}
		
		fun subparse(parser: Parser): Result {
			var local_parser = parser.clone().trim { c -> c.isWhitespace() }
			if (Expression.is_reserved(local_parser.src[0])) {
				return Err("Expected a Variable expression but none was found", local_parser.index)
			}
			var name = local_parser.collect(Expression::is_var_char)
			val id   = local_parser.env.register(name)
			var body = if (local_parser[0] == '.') {
				Application.parse(local_parser.advance(1))
			} else if (local_parser[0].isWhitespace()) {
				local_parser.trim({ it.isWhitespace() })
				Lambda.subparse(local_parser)
			} else {
				return Err("Expected a lambda body", local_parser.index, true)
			}
			local_parser.env.unregister(name)
			return when (body) {
				is Success -> Success(body.parser, Lambda(Variable(name, id), body.expr))
				is Err     -> body
			}
		}
	}
	
	override fun toString() =">${toSubstring()}"

	fun toSubstring(): String =
		if (expr is Lambda) {
			"${param} ${expr.toSubstring()}"
		} else {
			"${param}.${expr}"
		}
}

class Ordering {
	companion object {
		fun parse(parser: Parser): Result {
			var local_parser = parser.clone()
			if (local_parser[0] != '(') {
				return Err("Expected an ordering expression but none was found", parser.index)
			}
			var result = Application.parse(local_parser.advance(1))
			if (result is Success) {
				if (result.parser[0] != ')') {
					return Err("Missing closing ')'", result.parser.index, true)
				} else {
					result.parser.advance(1)
				}
			}
			return result
		}
	}
}

fun evaluate(src: String) {
	val result = Expression.parse(src)
	when (result) {
		is Success -> {
			println(result.expr.beta_normal())
		}
		is Err -> {
			println(src)
			println("${" ".repeat(result.index)}^ ${result.message}")
		}
	}
}

fun run_file(path: String) {
	val file = fopen(path, "r")
	if (file == null) {
		println("Could not load file '${path}'")
		return
	}
	try {
		memScoped {
			fseek(file, 0, SEEK_END)
			val size = ftell(file).toInt()
			fseek(file, 0, SEEK_SET)

			val buffer = allocArray<ByteVar>(size + 1)
			fread(buffer, 1, size.toULong(), file)
			buffer[size] = 0.toByte()

			buffer
				.toKString()
				.split('\n')
				.filter {
					it.length > 0
				}
				.forEach {
					println("glam) ${it}")
					evaluate(it)
				}
		}
	} finally {
		fclose(file)
	}
}

fun main(args: Array<String>) {
	if (args.size > 0) {
		run_file(args[0])
	} else {
		var repl = true
		while(repl) {
			print("glam) ")
			val input = readLine()
			val command = if (input == null) {
				println("Error reading input")
				continue
			} else {
				input.split(' ')
			}

			val ESCAPE = "\\"
			if (command[0].startsWith(ESCAPE)) {
				when (command[0].substring(ESCAPE.length)) {
					"q", "quit", "exit" -> repl = false
					"exec" -> {
						if (command.size < 2) {
							println("'exec' requires a file path as an argument")
							continue
						}
						run_file(command[1])
					}
					"h", "help" -> {
						println("\\q, \\quit, \\exit -> Exit the shell")
						println("\\exec [script] -> Execute a script file")
						println("\\help -> List shell commands")
					}
					else -> println("Unrecognized command '${input}'")
				}
			} else {
				evaluate(input)
			}
		}
	}
}


