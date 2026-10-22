import React from 'react';

type Variant = 'default' | 'accent' | 'success' | 'warning' | 'danger' | 'muted';

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: Variant;
  children: React.ReactNode;
}

export const Badge: React.FC<BadgeProps> = ({ variant = 'default', className, children, ...props }) => {
  const base = 'inline-block px-2 py-0.5 text-xs font-medium rounded-sm shadow-elev-1';
  const colors: Record<Variant, string> = {
    default: 'bg-gray-700 text-gray-100',
    accent: 'bg-huddle-coral text-white',
    success: 'bg-green-600 text-white',
    warning: 'bg-yellow-600 text-white',
    danger: 'bg-red-600 text-white',
    muted: 'bg-gray-500 text-gray-100',
  };
  return (
    <span className={`${base} ${colors[variant]} ${className ?? ''}`} {...props}>
      {children}
    </span>
  );
};
export default Badge;
