import org.gradle.api.Task

inline operator fun <T : Task> T.invoke(closure: T.() -> Unit): T {
    closure()
    return this
}
