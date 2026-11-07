import SwiftUI

@main struct YanJingApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environment(\.locale, Locale(identifier: model.language.resolved))
                .preferredColorScheme(model.appearance == "system" ? nil : model.appearance == "dark" ? .dark : .light)
                .task { model.load() }
        }
    }
}
