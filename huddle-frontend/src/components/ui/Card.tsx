import React from 'react';

export const Card: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({ className, children, ...props }) => (
  <div className={`dashboard-card rounded-xl shadow-elev-2 ${className ?? ''}`} {...props}>
    {children}
  </div>
);
export default Card;
