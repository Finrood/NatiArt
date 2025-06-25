export const environment = {
  production: false,
  api: {
    directory: {
      url: 'http://localhost:8081',
      endpoints: {
        login: '/login',
        logout: '/logout',
        current: '/current',
        user: '/users',
      }
    },
    product: {
      url: 'http://localhost:8082',
      endpoints: {
        category: '/categories',
        package: '/packages',
        product: '/products',
        order: '/orders',
      }
    },
  }
};
