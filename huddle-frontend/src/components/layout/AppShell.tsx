"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { 
  Home, Compass, FolderGit2, Users, MessageSquare, Bell, 
  Briefcase, Bookmark, BarChart2, Settings, HelpCircle, PanelLeftClose,
  Search, Plus, ChevronDown, Menu, Sparkles, Inbox, ShieldAlert, LogOut
} from "lucide-react";
import { notificationService, requestService } from "@/lib/api";
import { logout } from "@/lib/auth";
import { InstitutionStatusBanner } from "./InstitutionStatusBanner";

export function AppShell({ children }: { children: React.ReactNode }) {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadMessageCount, setUnreadMessageCount] = useState(0);
  const [isAdmin, setIsAdmin] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    // Decode token to see if user has ROLE_ADMIN or ADMIN
    if (typeof window !== "undefined") {
      const token = localStorage.getItem("huddle_token");
      if (token) {
        try {
          const payload = JSON.parse(atob(token.split(".")[1]));
          const roles: string[] = payload.roles || [];
          if (roles.includes("ROLE_ADMIN") || roles.includes("ADMIN")) {
            setIsAdmin(true);
          }
        } catch {}
      }
    }

    const fetchCounts = () => {
      notificationService.getUnreadCount()
        .then((count) => setUnreadCount(count))
        .catch(() => {}); // Fail silently
      
      requestService.getUnreadMessageCount()
        .then((count) => setUnreadMessageCount(count))
        .catch(() => {});
    };

    fetchCounts();
    const interval = setInterval(fetchCounts, 30000);
    
    window.addEventListener("refresh-badges", fetchCounts);
    
    return () => {
      clearInterval(interval);
      window.removeEventListener("refresh-badges", fetchCounts);
    };
  }, []);

  const navItems = [
    { section: "Main", items: [
      { name: "Home", href: "/", icon: Home },
      { name: "Browse", href: "/projects", icon: Compass, activeColor: "text-huddle-coral" },
      { name: "Matched", href: "/matched", icon: Sparkles, activeColor: "text-huddle-purple" },
      { name: "People", href: "/people", icon: Users },
      { name: "Messages", href: "/messages", icon: MessageSquare, badge: unreadMessageCount > 0 ? unreadMessageCount : undefined },
      { name: "Requests", href: "/requests", icon: Inbox },
      { name: "Notifications", href: "/notifications", icon: Bell, badge: unreadCount > 0 ? unreadCount : undefined },
    ]},
    { section: "Workspace", items: [
      { name: "Collaborations", href: "/collaborations", icon: Users },
      { name: "My Projects", href: "/my-projects", icon: Briefcase },
      ...(isAdmin ? [{ name: "Admin", href: "/admin", icon: ShieldAlert, activeColor: "text-red-400" }] : []),
    ]},
  ];


  const bottomItems = [
    { name: "Settings", href: "/profile", icon: Settings },
    { name: "Help", href: "/help", icon: HelpCircle },
  ];

  return (
    <div className="flex h-screen overflow-hidden bg-huddle-black text-white selection:bg-huddle-pink selection:text-white">
      {/* Mobile Menu Backdrop */}
      {mobileMenuOpen && (
        <div 
          className="fixed inset-0 bg-black/60 z-[90] md:hidden backdrop-blur-sm"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}
      
      {/* Sidebar Navigation */}
      <aside 
        className={`border-r border-white/5 bg-huddle-black ${mobileMenuOpen ? 'flex absolute inset-y-0 left-0 h-full w-64 z-[100]' : 'hidden md:flex relative'} flex-col justify-between flex-shrink-0 transition-all duration-300 ${isCollapsed && !mobileMenuOpen ? 'w-20' : 'w-64'}`}
      >
        <div className="flex flex-col flex-1 overflow-hidden">
          {/* App Logo */}
          <div className="h-20 flex items-center px-6 flex-shrink-0 justify-center">
            <Link href="/" className="flex items-center group transition-all duration-300 w-full">
              <div className="flex-shrink-0 relative w-6 h-6">
                <div className="absolute w-[9px] h-[18px] left-[1px] top-[3px] bg-huddle-coral rounded-logo -skew-y-[15deg] transition-transform group-hover:scale-105" />
                <div className="absolute w-[9px] h-[18px] right-[1px] top-[3px] bg-huddle-purple rounded-logo skew-y-[15deg] transition-transform group-hover:scale-105" />
                <div className="absolute w-[12px] h-[6px] left-[6px] top-[9px] bg-gradient-to-r from-huddle-coral to-huddle-purple rounded-sm" />
              </div>
              {!isCollapsed && <span className="font-display font-bold text-xl tracking-tight text-white ml-3 whitespace-nowrap overflow-hidden transition-all duration-300">Huddle</span>}
            </Link>
          </div>

          {/* Scrollable Nav Links */}
          <div className="flex-1 overflow-y-auto hide-scrollbar px-3 py-2 flex flex-col gap-6">
            {navItems.map((section) => (
              <div key={section.section}>
                {!isCollapsed && (
                  <div className="text-[10px] font-semibold text-gray-500 uppercase tracking-wider mb-2 px-3 whitespace-nowrap overflow-hidden transition-all duration-300">
                    {section.section}
                  </div>
                )}
                <nav className="space-y-1">
                  {section.items.map((item) => {
                    const isActive = item.href === "/" ? pathname === "/" : (pathname === item.href || pathname.startsWith(item.href + "/"));
                    return (
                      <div key={item.name} className="relative group/nav-item">
                        <Link 
                          href={item.href}
                          className={`relative flex items-center ${isCollapsed ? 'justify-center px-0' : 'px-3'} py-2.5 rounded-lg text-sm font-medium transition-colors ${isActive ? 'text-white' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}
                        >
                          {isActive && !isCollapsed && (
                            <div className="absolute left-0 top-1/2 -translate-y-1/2 h-[60%] w-[3px] bg-gradient-to-b from-huddle-coral to-huddle-purple rounded-r" />
                          )}
                          {isActive && !isCollapsed && (
                            <div className="absolute inset-0 bg-gradient-to-r from-huddle-coral/10 to-transparent rounded-lg" />
                          )}
                          <div className="w-5 h-5 flex items-center justify-center flex-shrink-0 z-10">
                            <item.icon className={`w-5 h-5 transition-colors ${isActive ? (item.activeColor || 'text-huddle-coral') : 'text-gray-500 group-hover/nav-item:text-gray-300'}`} />
                          </div>
                          {!isCollapsed && <span className="ml-3 whitespace-nowrap z-10">{item.name}</span>}
                          {!isCollapsed && item.badge && (
                            <span className="ml-auto bg-huddle-pink text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full z-10">{item.badge}</span>
                          )}
                        </Link>
                        {isCollapsed && (
                          <div className="absolute left-full top-1/2 -translate-y-1/2 ml-2 px-2.5 py-1.5 bg-huddle-charcoal border border-white/10 rounded-lg text-white text-xs font-medium opacity-0 group-hover/nav-item:opacity-100 pointer-events-none z-[100] transition-all whitespace-nowrap shadow-xl">
                            {item.name}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </nav>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom Actions */}
        <div className="px-3 pb-5 flex-shrink-0">
          <div className="h-px bg-white/5 mx-3 mb-4"></div>
          <nav className="space-y-1 mb-2">
            {bottomItems.map((item) => (
              <div key={item.name} className="relative group/nav-item">
                <Link href={item.href} className={`relative flex items-center ${isCollapsed ? 'justify-center px-0' : 'px-3'} py-2.5 rounded-lg text-sm font-medium text-gray-400 hover:text-white hover:bg-white/5 transition-colors`}>
                  <div className="w-5 h-5 flex items-center justify-center flex-shrink-0">
                    <item.icon className="w-5 h-5 text-gray-500 group-hover/nav-item:text-gray-300 transition-colors" />
                  </div>
                  {!isCollapsed && <span className="ml-3 whitespace-nowrap">{item.name}</span>}
                </Link>
                {isCollapsed && (
                  <div className="absolute left-full top-1/2 -translate-y-1/2 ml-2 px-2.5 py-1.5 bg-huddle-charcoal border border-white/10 rounded-lg text-white text-xs font-medium opacity-0 group-hover/nav-item:opacity-100 pointer-events-none z-[100] transition-all whitespace-nowrap shadow-xl">
                    {item.name}
                  </div>
                )}
              </div>
            ))}
          </nav>
          {/* Logout Button */}
          <div className="relative group/nav-item mb-1">
            <button
              onClick={logout}
              className={`w-full relative flex items-center ${isCollapsed ? 'justify-center px-0' : 'px-3'} py-2.5 rounded-lg text-sm font-medium text-gray-400 hover:text-red-400 hover:bg-red-500/5 transition-colors group/logout`}
              aria-label="Log out"
            >
              <div className="w-5 h-5 flex items-center justify-center flex-shrink-0">
                <LogOut className="w-5 h-5 text-gray-500 group-hover/logout:text-red-400 transition-colors" />
              </div>
              {!isCollapsed && <span className="ml-3 whitespace-nowrap">Log out</span>}
            </button>
            {isCollapsed && (
              <div className="absolute left-full top-1/2 -translate-y-1/2 ml-2 px-2.5 py-1.5 bg-huddle-charcoal border border-white/10 rounded-lg text-white text-xs font-medium opacity-0 group-hover/nav-item:opacity-100 pointer-events-none z-[100] transition-all whitespace-nowrap shadow-xl">
                Log out
              </div>
            )}
          </div>
          <button 
            onClick={() => setIsCollapsed(!isCollapsed)}
            className={`w-full flex items-center ${isCollapsed ? 'justify-center px-0' : 'px-3'} py-2.5 rounded-lg text-sm font-medium text-gray-500 hover:text-white hover:bg-white/5 transition-colors group/toggle`}
          >
            <div className="w-5 h-5 flex items-center justify-center flex-shrink-0">
              <PanelLeftClose className={`w-5 h-5 transition-transform duration-300 group-hover/toggle:text-gray-300 ${isCollapsed ? 'rotate-180' : ''}`} />
            </div>
            {!isCollapsed && <span className="ml-3 whitespace-nowrap">Collapse Sidebar</span>}
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col h-screen overflow-hidden bg-huddle-black relative">
        {/* Subtle Ambient Background Light */}
        <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-huddle-purple opacity-[0.03] blur-[120px] rounded-full pointer-events-none z-0" />
        <div className="absolute top-40 right-1/4 w-[400px] h-[400px] bg-huddle-coral opacity-[0.03] blur-[100px] rounded-full pointer-events-none z-0" />

        {/* Top Header Bar */}
        <header className="h-20 flex items-center justify-between px-6 md:px-10 border-b border-white/5 bg-huddle-black z-10 sticky top-0 flex-shrink-0">
          <button className="md:hidden text-gray-400 hover:text-white transition-colors" onClick={() => setMobileMenuOpen(!mobileMenuOpen)}>
            <Menu className="w-6 h-6" />
          </button>

          <div className="hidden md:flex flex-1 max-w-2xl ml-4 mr-8">
            <div className="relative w-full border border-white/10 rounded-full bg-huddle-dark overflow-hidden transition-all duration-300 focus-within:border-huddle-coral/50 focus-within:shadow-elev-1">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                <Search className="w-4 h-4 text-gray-500" />
              </div>
              <input 
                type="text" 
                className="block w-full pl-11 pr-4 py-2.5 bg-transparent border-none outline-none text-sm text-white placeholder-gray-500" 
                placeholder="Search projects, skills, or people..."
              />
              <div className="absolute inset-y-0 right-0 pr-2 flex items-center">
                <span className="text-[10px] font-medium text-gray-500 border border-white/10 rounded px-1.5 py-0.5 bg-white/5">Ctrl+K</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-5 ml-auto">
            <Link href="/create-post" className="text-gray-400 hover:text-white transition-colors bg-white/5 p-2 rounded-full border border-white/5 hover:bg-white/10 hidden md:block">
              <Plus className="w-4 h-4" />
            </Link>
            <div className="h-6 w-px bg-white/10 hidden md:block" />
            <Link href="/profile" className="flex items-center gap-2 hover:opacity-80 transition-opacity focus:outline-none">
              <img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80" alt="Profile" className="w-8 h-8 rounded-full border border-white/10 object-cover" />
              <ChevronDown className="w-4 h-4 text-gray-500 hidden sm:block" />
            </Link>
          </div>
        </header>
        <InstitutionStatusBanner />

        {/* Scrollable Content */}
        <div className={`flex-1 z-10 relative ${(pathname === '/chat' || pathname === '/messages') ? 'overflow-hidden flex flex-col' : 'overflow-y-auto p-6 md:p-10 custom-scrollbar'}`}>
          {children}
        </div>
      </main>
    </div>
  );
}
