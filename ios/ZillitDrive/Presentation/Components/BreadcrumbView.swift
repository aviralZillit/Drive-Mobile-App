import SwiftUI

struct BreadcrumbBar: View {
    let breadcrumbs: [BreadcrumbItem]
    let onTap: (BreadcrumbItem) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    ForEach(Array(breadcrumbs.enumerated()), id: \.element.name) { index, crumb in
                        if index > 0 {
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }

                        Button {
                            onTap(crumb)
                        } label: {
                            Text(crumb.name)
                                .font(.subheadline)
                                .fontWeight(index == breadcrumbs.count - 1 ? .semibold : .regular)
                                .foregroundColor(index == breadcrumbs.count - 1 ? .primary : .secondary)
                        }
                        .id(crumb.name)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            .background(Color(.systemGroupedBackground))
            .onChange(of: breadcrumbs.count) { _ in
                if let last = breadcrumbs.last {
                    withAnimation {
                        proxy.scrollTo(last.name, anchor: .trailing)
                    }
                }
            }
        }
    }
}
