package com.github.itmosoftwaredesign.cli

import com.github.itmosoftwaredesign.cli.command.Command
import com.github.itmosoftwaredesign.cli.command.CommandRegistry
import com.github.itmosoftwaredesign.cli.command.parser.CommandParser
import com.github.itmosoftwaredesign.cli.command.parser.ParsedCommand
import io.mockk.*

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.*

class InterpreterTest {

    private lateinit var environment: Environment
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var commandParser: CommandParser
    private lateinit var inputStream: InputStream
    private lateinit var interpreter: Interpreter

    @BeforeEach
    fun setUp() {
        environment = mockk(relaxed = true)
        commandRegistry = mockk(relaxed = true)
        commandParser = mockk(relaxed = true)
    }

    @Test
    fun `should execute known command`() {
        val commandMock = mockk<Command>(relaxed = true)
        val parsedCommand = mockk<ParsedCommand>(relaxed = true)
        val commandTokens = listOf("echo", "Hello")

        inputStream = ByteArrayInputStream("echo Hello\nexit\n".toByteArray())
        every { parsedCommand.commandTokens } returns commandTokens
        every { commandParser.parse("echo Hello") } returns parsedCommand
        every { commandRegistry["echo"] } returns commandMock

        interpreter = Interpreter(environment, commandParser, commandRegistry, inputStream)
        interpreter.run()

        verify { commandMock.execute(environment, parsedCommand.inputStream, parsedCommand.outputStream, parsedCommand.errorStream, listOf("Hello")) }
    }

    @Test
    fun `should run external process on unknown command`() {
        inputStream = ByteArrayInputStream("ls\n".toByteArray())
        interpreter = spyk(Interpreter(environment, commandParser, commandRegistry, inputStream))
        val parsedCommand = mockk<ParsedCommand>()
        every { commandParser.parse(any()) } returns parsedCommand
        every { parsedCommand.commandTokens } returns listOf("unknownCommand")
        every { commandRegistry["unknownCommand"] } returns null

        interpreter.run()

        verify { interpreter.runExternalCommand(parsedCommand, listOf()) }
    }

    @Test
    fun `should exit on exit command`() {
        inputStream = ByteArrayInputStream("exit\n".toByteArray())
        val parsedCommand = mockk<ParsedCommand>(relaxed = true)
        every { commandParser.parse("exit") } returns parsedCommand
        every { parsedCommand.commandTokens } returns listOf("exit")

        interpreter = Interpreter(environment, commandParser, commandRegistry, inputStream)

        interpreter.run()

        verify(exactly = 0) { commandRegistry["exit"] }
    }

    @Test
    fun `should handle command execution exception`() {
        val commandMock = mockk<Command>(relaxed = true)
        val parsedCommand = mockk<ParsedCommand>(relaxed = true)
        val commandTokens = listOf("failingCommand")

        inputStream = ByteArrayInputStream("failingCommand\nexit\n".toByteArray())
        every { parsedCommand.commandTokens } returns commandTokens
        every { commandParser.parse("failingCommand") } returns parsedCommand
        every { commandRegistry["failingCommand"] } returns commandMock
        every {
            commandMock.execute(environment, any(), any(), any(), any())
        } throws RuntimeException("Execution failed")

        val errorOutput = ByteArrayOutputStream()
        System.setErr(PrintStream(errorOutput))

        interpreter = Interpreter(environment, commandParser, commandRegistry, inputStream)
        interpreter.run()

        assert(errorOutput.toString().contains("Command 'failingCommand' execution exception"))
    }
}
