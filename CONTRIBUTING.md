# Contributing Guide

Thank you for your interest in contributing to Wake Up! This document provides guidelines for contributing to the project.

## 🚀 How to Contribute

### Reporting a Bug

If you find a bug, please open an issue with:
- A clear description of the problem
- Steps to reproduce the bug
- Expected behavior
- Observed behavior
- Android version and phone model (if applicable)
- Screenshots (if applicable)

### Proposing a Feature

Feature suggestions are welcome! Please open an issue with:
- A clear description of the feature
- Use case and why it would be useful
- UI examples if applicable

### Submitting a Pull Request

1. **Fork the project**
   ```bash
   git clone https://github.com/epascal/wakeup.git
   cd wakeup
   ```

2. **Create a branch**
   ```bash
   git checkout -b feature/my-new-feature
   # or
   git checkout -b fix/bug-fix
   ```

3. **Make your changes**
   - Follow existing code style
   - Add comments if necessary
   - Test your changes

4. **Commit your changes**
   ```bash
   git add .
   git commit -m "Clear description of your changes"
   ```
   
   **Commit conventions** :
   - `feat:` for a new feature
   - `fix:` for a bug fix
   - `docs:` for documentation
   - `style:` for formatting
   - `refactor:` for refactoring
   - `test:` for tests
   - `chore:` for maintenance tasks

5. **Push to your fork**
   ```bash
   git push origin feature/my-new-feature
   ```

6. **Open a Pull Request**
   - Go to GitHub
   - Click "New Pull Request"
   - Select your branch
   - Describe your changes

## 📋 Code Standards

### Java Code Style

- Follow standard Java conventions
- Use descriptive variable names
- Comment complex code
- Handle exceptions appropriately

### Formatting

- Indentation: 4 spaces
- Line length: 100 characters maximum (if possible)
- Use braces even for single-line if/for if it improves readability

### Structure

- One class per file
- Organize methods logically
- Separate responsibilities

## 🧪 Testing

Before submitting a PR:

1. **Build the project**
   ```bash
   ./gradlew clean build
   ```

2. **Test on a real device or emulator**
   - Test modified features
   - Verify there are no regressions

3. **Check logs**
   ```bash
   adb logcat | grep WakeUp
   ```

## 📝 Documentation

- Update README.md if necessary
- Add Javadoc comments for new public methods
- Update CHANGELOG.md with your changes

## ✅ Checklist Before Submitting

- [ ] Code compiles without errors
- [ ] Tests pass (if applicable)
- [ ] Code follows project conventions
- [ ] Documentation is up to date
- [ ] Commits are clear and descriptive
- [ ] PR has a clear description

## 🎯 Priorities

Contributions are particularly welcome for:

- Permission management improvements
- Performance optimization
- User interface improvements
- Support for new Android features
- Bug fixes
- Documentation improvements

## 📧 Questions?

If you have questions, feel free to open an issue with the `question` tag.

Thank you for contributing to Wake Up! 🎉
